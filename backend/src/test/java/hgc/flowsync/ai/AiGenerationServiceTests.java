package hgc.flowsync.ai;

import java.time.LocalDate;
import java.util.List;

import hgc.flowsync.common.error.BusinessException;
import hgc.flowsync.common.error.ErrorCode;
import hgc.flowsync.project.Priority;
import hgc.flowsync.project.ProjectStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiGenerationServiceTests {

	private final AiContextService contextService = mock(AiContextService.class);
	private final OpenAiCompatibleClient aiClient = mock(OpenAiCompatibleClient.class);
	private final Authentication authentication = mock(Authentication.class);
	private AiGenerationService service;

	@BeforeEach
	void setUp() {
		service = new AiGenerationService(
			contextService, aiClient, new ObjectMapper().findAndRegisterModules());
		when(contextService.taskPlan(any(), any())).thenReturn(new AiContextService.TaskPlanContext(
			"Project", null, ProjectStatus.NOT_STARTED, Priority.MEDIUM,
			LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 31),
			List.of(new AiContextService.MemberContext("2", "Member"))));
		when(aiClient.responseFormatMode()).thenReturn(AiProperties.ResponseFormat.NONE);
	}

	@Test
	void rejectsInvalidProviderGraphAndAssignee() {
		for (String response : List.of(
			plan("a", "a", "2", "2026-07-20"),
			plan("a", "missing", "2", "2026-07-20"),
			plan("a", null, "999", "2026-07-20"),
			plan("a", null, "2", "2026-09-01"))) {
			when(aiClient.generatePlan(anyString(), anyString())).thenReturn(response);
			assertThatThrownBy(() -> service.generatePlan(
				authentication, 1L, new AiTaskPlanGenerateRequest("Goal", null, null)))
				.isInstanceOf(BusinessException.class)
				.satisfies(exception -> assertThat(((BusinessException) exception).code())
					.isEqualTo(ErrorCode.AI_PROVIDER_ERROR));
		}
	}

	@Test
	void rejectsMalformedTruncatedAndMixedProviderOutput() {
		String valid = plan("a", null, "2", "2026-07-20");
		for (String response : List.of(
			"not json",
			"```json\n{\"overview\":\"truncated\"\n```",
			"Here is the plan: {\"overview\":\"Plan\",\"items\":[]}",
			valid + " trailing prose",
			valid + valid,
			"```json\n" + valid + "\n``` trailing prose")) {
			when(aiClient.generatePlan(anyString(), anyString())).thenReturn(response);
			assertThatThrownBy(() -> service.generatePlan(
				authentication, 1L, new AiTaskPlanGenerateRequest("Goal", null, null)))
				.isInstanceOf(BusinessException.class)
				.satisfies(exception -> assertThat(((BusinessException) exception).code())
					.isEqualTo(ErrorCode.AI_PROVIDER_ERROR));
		}
	}

	@Test
	void rejectsUnknownAndDuplicateProviderFields() {
		for (String response : List.of(
			"""
				{"overview":"Plan","unknown":true,"items":[{"draftId":"a","parentDraftId":null,
				"title":"Task","description":null,"priority":"MEDIUM","dueDate":null,
				"assigneeId":"2"}]}
				""",
			"""
				{"overview":"Plan","items":[{"draftId":"a","parentDraftId":null,
				"title":"First","title":"Second","description":null,"priority":"MEDIUM",
				"dueDate":null,"assigneeId":"2"}]}
				""")) {
			when(aiClient.generatePlan(anyString(), anyString())).thenReturn(response);
			assertThatThrownBy(() -> service.generatePlan(
				authentication, 1L, new AiTaskPlanGenerateRequest("Goal", null, null)))
				.isInstanceOf(BusinessException.class)
				.satisfies(exception -> assertThat(((BusinessException) exception).code())
					.isEqualTo(ErrorCode.AI_PROVIDER_ERROR));
		}
	}

	@Test
	void retriesOneRejectedPlanAndReturnsTheNextValidResponse() {
		when(aiClient.generatePlan(anyString(), anyString()))
			.thenReturn("not json")
			.thenReturn(plan("a", null, "2", "2026-07-20"));

		AiTaskPlanResponse response = service.generatePlan(
			authentication, 1L, new AiTaskPlanGenerateRequest("Goal", null, null));

		assertThat(response.items()).hasSize(1);
		verify(aiClient, org.mockito.Mockito.times(2)).generatePlan(anyString(), anyString());
	}

	private static String plan(
		String draftId,
		String parentDraftId,
		String assigneeId,
		String dueDate) {
		return """
			{"overview":"Plan","items":[{"draftId":"%s","parentDraftId":%s,"title":"Task",
			"description":null,"priority":"MEDIUM","dueDate":"%s","assigneeId":"%s"}]}
			""".formatted(
				draftId,
				parentDraftId == null ? "null" : "\"" + parentDraftId + "\"",
				dueDate,
				assigneeId);
	}
}
