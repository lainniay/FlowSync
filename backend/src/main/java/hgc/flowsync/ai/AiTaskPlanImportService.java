package hgc.flowsync.ai;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import hgc.flowsync.common.error.BusinessException;
import hgc.flowsync.common.error.ErrorCode;
import hgc.flowsync.project.Project;
import hgc.flowsync.project.ProjectMemberMapper;
import hgc.flowsync.task.Task;
import hgc.flowsync.task.TaskAccessService;
import hgc.flowsync.task.TaskMapper;
import hgc.flowsync.task.TaskResponse;
import hgc.flowsync.task.TaskStatus;
import hgc.flowsync.user.CurrentUserService;
import hgc.flowsync.user.SystemRole;
import hgc.flowsync.user.User;
import hgc.flowsync.user.UserWriteLockService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiTaskPlanImportService {

	private final TaskMapper taskMapper;
	private final ProjectMemberMapper projectMemberMapper;
	private final CurrentUserService currentUserService;
	private final UserWriteLockService userWriteLockService;
	private final TaskAccessService taskAccessService;

	public AiTaskPlanImportService(
		TaskMapper taskMapper,
		ProjectMemberMapper projectMemberMapper,
		CurrentUserService currentUserService,
		UserWriteLockService userWriteLockService,
		TaskAccessService taskAccessService) {
		this.taskMapper = taskMapper;
		this.projectMemberMapper = projectMemberMapper;
		this.currentUserService = currentUserService;
		this.userWriteLockService = userWriteLockService;
		this.taskAccessService = taskAccessService;
	}

	@Transactional
	public AiTaskPlanImportResponse importPlan(
		Authentication authentication,
		Long projectId,
		List<AiTaskPlanItem> items) {
		User requestedBy = currentUserService.require(authentication);
		Long[] assigneeIds = new Long[items.size()];
		for (int index = 0; index < items.size(); index++) {
			assigneeIds[index] = parseAssigneeId(items.get(index).assigneeId(), index);
		}
		UserWriteLockService.LockedUsers lockedUsers =
			userWriteLockService.lockUsers(requestedBy, assigneeIds);
		TaskAccessService.ProjectContext context =
			taskAccessService.requireCreatable(authentication, projectId);

		Map<String, Integer> indexByDraftId = validateDraftIds(items);
		validateItems(context.project(), items, assigneeIds, lockedUsers, indexByDraftId);

		Task[] imported = new Task[items.size()];
		for (int index = 0; index < items.size(); index++) {
			insert(index, items, assigneeIds, indexByDraftId, imported, context);
		}
		List<TaskResponse> responses = new ArrayList<>(items.size());
		for (Task task : imported) {
			Task stored = taskMapper.selectById(task.getId());
			responses.add(TaskResponse.from(
				stored,
				context.currentUser(),
				stored.getAssigneeId() == null ? null : lockedUsers.user(stored.getAssigneeId()),
				0));
		}
		return new AiTaskPlanImportResponse(responses.size(), List.copyOf(responses));
	}

	private Map<String, Integer> validateDraftIds(List<AiTaskPlanItem> items) {
		Map<String, Integer> indexByDraftId = new HashMap<>();
		for (int index = 0; index < items.size(); index++) {
			if (indexByDraftId.putIfAbsent(items.get(index).draftId(), index) != null) {
				throw validation(index, "draftId");
			}
		}
		return indexByDraftId;
	}

	private void validateItems(
		Project project,
		List<AiTaskPlanItem> items,
		Long[] assigneeIds,
		UserWriteLockService.LockedUsers lockedUsers,
		Map<String, Integer> indexByDraftId) {
		for (int index = 0; index < items.size(); index++) {
			AiTaskPlanItem item = items.get(index);
			if (item.parentDraftId() != null && !indexByDraftId.containsKey(item.parentDraftId())) {
				throw validation(index, "parentDraftId");
			}
			validateAssignee(project, assigneeIds[index], lockedUsers, index);
			validateDueDate(project, item.dueDate(), index);
		}
		int[] states = new int[items.size()];
		for (int index = 0; index < items.size(); index++) {
			validateAcyclic(index, items, indexByDraftId, states);
		}
	}

	private void validateAcyclic(
		int index,
		List<AiTaskPlanItem> items,
		Map<String, Integer> indexByDraftId,
		int[] states) {
		if (states[index] == 2) {
			return;
		}
		states[index] = 1;
		String parentDraftId = items.get(index).parentDraftId();
		if (parentDraftId != null) {
			int parentIndex = indexByDraftId.get(parentDraftId);
			if (states[parentIndex] == 1) {
				throw validation(index, "parentDraftId");
			}
			if (states[parentIndex] == 0) {
				validateAcyclic(parentIndex, items, indexByDraftId, states);
			}
		}
		states[index] = 2;
	}

	private void validateAssignee(
		Project project,
		Long assigneeId,
		UserWriteLockService.LockedUsers lockedUsers,
		int index) {
		if (assigneeId == null) {
			return;
		}
		User assignee = lockedUsers.user(assigneeId);
		if (assignee == null
			|| !assignee.isActive()
			|| assignee.getSystemRole() != SystemRole.USER
			|| !projectMemberMapper.existsByProjectIdAndUserIdForUpdate(
				project.getId(), assigneeId)) {
			throw validation(index, "assigneeId");
		}
	}

	private static void validateDueDate(Project project, LocalDate dueDate, int index) {
		if (dueDate != null
			&& ((project.getStartDate() != null && dueDate.isBefore(project.getStartDate()))
				|| (project.getEndDate() != null && dueDate.isAfter(project.getEndDate())))) {
			throw validation(index, "dueDate");
		}
	}

	private void insert(
		int index,
		List<AiTaskPlanItem> items,
		Long[] assigneeIds,
		Map<String, Integer> indexByDraftId,
		Task[] imported,
		TaskAccessService.ProjectContext context) {
		if (imported[index] != null) {
			return;
		}
		AiTaskPlanItem item = items.get(index);
		Task parent = null;
		if (item.parentDraftId() != null) {
			int parentIndex = indexByDraftId.get(item.parentDraftId());
			insert(parentIndex, items, assigneeIds, indexByDraftId, imported, context);
			parent = imported[parentIndex];
		}
		Task task = new Task();
		task.setProjectId(context.project().getId());
		task.setParentId(parent == null ? null : parent.getId());
		task.setAssigneeId(assigneeIds[index]);
		task.setCreatorId(context.currentUser().getId());
		task.setTitle(item.title());
		task.setDescription(item.description());
		task.setStatus(TaskStatus.NOT_STARTED);
		task.setPriority(item.priority());
		task.setDueDate(item.dueDate());
		taskMapper.insert(task);
		imported[index] = task;
	}

	private static Long parseAssigneeId(String value, int index) {
		if (value == null) {
			return null;
		}
		try {
			return Long.parseLong(value);
		} catch (NumberFormatException exception) {
			throw validation(index, "assigneeId");
		}
	}

	private static BusinessException validation(int index, String field) {
		return new BusinessException(
			ErrorCode.VALIDATION_ERROR,
			"items[" + index + "]." + field);
	}
}
