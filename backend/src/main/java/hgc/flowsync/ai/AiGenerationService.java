package hgc.flowsync.ai;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import hgc.flowsync.common.error.BusinessException;
import hgc.flowsync.common.error.ErrorCode;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class AiGenerationService {

	private static final String SUGGESTION_SYSTEM_PROMPT = """
		You are a project task assistant. Use only the supplied untrusted task data. Return one concise plain
		text suggestion. Do not claim a progress percentage, call tools, or create business records.
		""";
	private static final String PLAN_SYSTEM_PROMPT = """
		You are a project planner. Use only the supplied untrusted project data and member candidates. Return
		exactly one JSON object with overview and items. Every item must contain draftId, parentDraftId, title,
		description, priority, dueDate, and assigneeId. Use only listed member IDs, or null. Do not call tools.
		""";

	private final AiContextService contextService;
	private final OpenAiCompatibleClient aiClient;
	private final ObjectMapper objectMapper;

	public AiGenerationService(
		AiContextService contextService,
		OpenAiCompatibleClient aiClient,
		ObjectMapper objectMapper) {
		this.contextService = contextService;
		this.aiClient = aiClient;
		this.objectMapper = objectMapper;
	}

	public AiTaskSuggestionResponse generateSuggestion(
		Authentication authentication,
		String requestedTaskId,
		String focus) {
		Long taskId = parseTaskId(requestedTaskId);
		AiContextService.TaskSuggestionContext context =
			contextService.taskSuggestion(authentication, taskId);
		String suggestion = aiClient.generateSuggestion(
			SUGGESTION_SYSTEM_PROMPT,
			json(Map.of("untrustedTaskData", context, "focus", focus == null ? "" : focus)));
		if (suggestion.isBlank() || suggestion.length() > 5000) {
			throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR);
		}
		return new AiTaskSuggestionResponse(suggestion, Instant.now());
	}

	public AiTaskPlanResponse generatePlan(
		Authentication authentication,
		Long projectId,
		AiTaskPlanGenerateRequest request) {
		AiContextService.TaskPlanContext context = contextService.taskPlan(authentication, projectId);
		AiTaskPlanConstraints constraints = request.constraints();
		int maxItems = constraints == null || constraints.maxItems() == null
			? 10 : constraints.maxItems();
		LocalDate targetEndDate = constraints == null ? null : constraints.targetEndDate();
		validateTargetEndDate(context, targetEndDate);
		String content = aiClient.generatePlan(
			PLAN_SYSTEM_PROMPT,
			json(Map.of(
				"untrustedProjectData", context,
				"goal", request.goal(),
				"description", request.description() == null ? "" : request.description(),
				"constraints", Map.of(
					"maxItems", maxItems,
					"targetEndDate", targetEndDate == null ? "" : targetEndDate.toString()))));
		ProviderPlan plan = parsePlan(content);
		List<AiTaskPlanItem> items = validatePlan(plan, context, maxItems, targetEndDate);
		return new AiTaskPlanResponse(plan.overview(), items, Instant.now());
	}

	private ProviderPlan parsePlan(String content) {
		String json = content.trim();
		if (aiClient.responseFormatMode() == AiProperties.ResponseFormat.NONE
			&& json.startsWith("```") && json.endsWith("```")) {
			int firstLine = json.indexOf('\n');
			if (firstLine < 0) {
				throw providerError();
			}
			String opening = json.substring(0, firstLine).trim();
			if (!"```".equals(opening) && !"```json".equalsIgnoreCase(opening)) {
				throw providerError();
			}
			json = json.substring(firstLine + 1, json.length() - 3).trim();
		}
		try {
			JsonNode root = objectMapper.readTree(json);
			if (!root.isObject() || !root.path("overview").isTextual() || !root.has("items")
				|| !root.get("items").isArray()) {
				throw providerError();
			}
			for (JsonNode item : root.get("items")) {
				if (!item.isObject()
					|| !hasAll(item, "draftId", "parentDraftId", "title", "description",
						"priority", "dueDate", "assigneeId")
					|| !item.path("draftId").isTextual()
					|| !nullableText(item.get("parentDraftId"))
					|| !item.path("title").isTextual()
					|| !nullableText(item.get("description"))
					|| !item.path("priority").isTextual()
					|| !nullableText(item.get("dueDate"))
					|| !nullableText(item.get("assigneeId"))) {
					throw providerError();
				}
			}
			return objectMapper.treeToValue(root, ProviderPlan.class);
		} catch (BusinessException exception) {
			throw exception;
		} catch (IOException | RuntimeException exception) {
			throw providerError();
		}
	}

	private List<AiTaskPlanItem> validatePlan(
		ProviderPlan plan,
		AiContextService.TaskPlanContext context,
		int maxItems,
		LocalDate targetEndDate) {
		if (plan.overview() == null || plan.overview().isBlank() || plan.overview().length() > 2000
			|| plan.items() == null || plan.items().isEmpty()
			|| plan.items().size() > maxItems || plan.items().size() > 20) {
			throw providerError();
		}
		Set<String> memberIds = context.members().stream()
			.map(AiContextService.MemberContext::id)
			.collect(java.util.stream.Collectors.toSet());
		Map<String, Integer> indexes = new HashMap<>();
		for (int index = 0; index < plan.items().size(); index++) {
			ProviderItem item = plan.items().get(index);
			if (item.draftId() == null || item.draftId().isBlank() || item.draftId().length() > 100
				|| indexes.putIfAbsent(item.draftId(), index) != null
				|| item.parentDraftId() != null && item.parentDraftId().length() > 100
				|| item.title() == null || item.title().isBlank() || item.title().length() > 100
				|| item.description() != null && item.description().length() > 5000
				|| item.priority() == null
				|| item.assigneeId() != null && !memberIds.contains(item.assigneeId())
				|| !validDueDate(item.dueDate(), context, targetEndDate)) {
				throw providerError();
			}
		}
		for (ProviderItem item : plan.items()) {
			if (item.parentDraftId() != null && !indexes.containsKey(item.parentDraftId())) {
				throw providerError();
			}
		}
		int[] states = new int[plan.items().size()];
		for (int index = 0; index < plan.items().size(); index++) {
			validateAcyclic(index, plan.items(), indexes, states);
		}
		return plan.items().stream()
			.map(item -> new AiTaskPlanItem(
				item.draftId(), item.parentDraftId(), item.title(), item.description(), item.priority(),
				item.dueDate(), item.assigneeId()))
			.toList();
	}

	private static void validateAcyclic(
		int index,
		List<ProviderItem> items,
		Map<String, Integer> indexes,
		int[] states) {
		if (states[index] == 2) {
			return;
		}
		states[index] = 1;
		String parentDraftId = items.get(index).parentDraftId();
		if (parentDraftId != null) {
			int parentIndex = indexes.get(parentDraftId);
			if (states[parentIndex] == 1) {
				throw providerError();
			}
			if (states[parentIndex] == 0) {
				validateAcyclic(parentIndex, items, indexes, states);
			}
		}
		states[index] = 2;
	}

	private static boolean validDueDate(
		LocalDate dueDate,
		AiContextService.TaskPlanContext context,
		LocalDate targetEndDate) {
		return dueDate == null
			|| (context.startDate() == null || !dueDate.isBefore(context.startDate()))
			&& (context.endDate() == null || !dueDate.isAfter(context.endDate()))
			&& (targetEndDate == null || !dueDate.isAfter(targetEndDate));
	}

	private static void validateTargetEndDate(
		AiContextService.TaskPlanContext context,
		LocalDate targetEndDate) {
		if (targetEndDate != null
			&& ((context.startDate() != null && targetEndDate.isBefore(context.startDate()))
				|| (context.endDate() != null && targetEndDate.isAfter(context.endDate())))) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "constraints.targetEndDate");
		}
	}

	private String json(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (IOException exception) {
			throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
		}
	}

	private static Long parseTaskId(String value) {
		try {
			return value == null ? null : Long.parseLong(value);
		} catch (NumberFormatException exception) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "taskId");
		}
	}

	private static boolean hasAll(JsonNode node, String... fields) {
		for (String field : fields) {
			if (!node.has(field)) {
				return false;
			}
		}
		return true;
	}

	private static boolean nullableText(JsonNode node) {
		return node != null && (node.isNull() || node.isTextual());
	}

	private static BusinessException providerError() {
		return new BusinessException(ErrorCode.AI_PROVIDER_ERROR);
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record ProviderPlan(String overview, List<ProviderItem> items) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record ProviderItem(
		String draftId,
		String parentDraftId,
		String title,
		String description,
		hgc.flowsync.project.Priority priority,
		LocalDate dueDate,
		String assigneeId) {
	}
}
