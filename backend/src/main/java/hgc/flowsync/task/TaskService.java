package hgc.flowsync.task;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import hgc.flowsync.common.api.PageResponse;
import hgc.flowsync.common.error.BusinessException;
import hgc.flowsync.common.error.ErrorCode;
import hgc.flowsync.project.Priority;
import hgc.flowsync.project.Project;
import hgc.flowsync.project.ProjectAccessService;
import hgc.flowsync.project.ProjectMember;
import hgc.flowsync.project.ProjectMemberMapper;
import hgc.flowsync.summary.Summary;
import hgc.flowsync.summary.SummaryMapper;
import hgc.flowsync.user.CurrentUserService;
import hgc.flowsync.user.SystemRole;
import hgc.flowsync.user.User;
import hgc.flowsync.user.UserMapper;
import hgc.flowsync.user.UserWriteLockService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class TaskService {

	private static final Set<String> TASK_REFERENCE_CONSTRAINTS = Set.of(
		"fk_tasks_parent",
		"fk_task_logs_task",
		"fk_summaries_task");

	private final TaskMapper taskMapper;
	private final TaskLogMapper taskLogMapper;
	private final SummaryMapper summaryMapper;
	private final UserMapper userMapper;
	private final ProjectMemberMapper projectMemberMapper;
	private final CurrentUserService currentUserService;
	private final ProjectAccessService projectAccessService;
	private final TaskAccessService taskAccessService;
	private final UserWriteLockService userWriteLockService;
	private final TransactionTemplate transactionTemplate;

	public TaskService(
		TaskMapper taskMapper,
		TaskLogMapper taskLogMapper,
		SummaryMapper summaryMapper,
		UserMapper userMapper,
		ProjectMemberMapper projectMemberMapper,
		CurrentUserService currentUserService,
		ProjectAccessService projectAccessService,
		TaskAccessService taskAccessService,
		UserWriteLockService userWriteLockService,
		PlatformTransactionManager transactionManager) {
		this.taskMapper = taskMapper;
		this.taskLogMapper = taskLogMapper;
		this.summaryMapper = summaryMapper;
		this.userMapper = userMapper;
		this.projectMemberMapper = projectMemberMapper;
		this.currentUserService = currentUserService;
		this.projectAccessService = projectAccessService;
		this.taskAccessService = taskAccessService;
		this.userWriteLockService = userWriteLockService;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
	}

	@Transactional(readOnly = true)
	public PageResponse<TaskResponse> findAll(
		Authentication authentication,
		String requestedProjectId,
		String requestedAssigneeId,
		TaskStatus status,
		Priority priority,
		String requestedParentId,
		LocalDate dueBefore,
		LocalDate dueAfter,
		Boolean incomplete,
		String q,
		int page,
		int size,
		String sort) {
		User currentUser = currentUserService.require(authentication);
		Long projectId = parseNullableId(requestedProjectId);
		Long assigneeId = parseNullableId(requestedAssigneeId);
		Long parentId = parseNullableId(requestedParentId);
		if (dueAfter != null && dueBefore != null && dueAfter.isAfter(dueBefore)) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR);
		}

		LambdaQueryWrapper<Task> query = Wrappers.<Task>lambdaQuery()
			.eq(projectId != null, Task::getProjectId, projectId)
			.eq(assigneeId != null, Task::getAssigneeId, assigneeId)
			.eq(status != null, Task::getStatus, status)
			.eq(priority != null, Task::getPriority, priority)
			.eq(parentId != null, Task::getParentId, parentId)
			.le(dueBefore != null, Task::getDueDate, dueBefore)
			.ge(dueAfter != null, Task::getDueDate, dueAfter)
			.notIn(Boolean.TRUE.equals(incomplete), Task::getStatus,
				TaskStatus.COMPLETED, TaskStatus.CANCELLED)
			.like(q != null && !q.isEmpty(), Task::getTitle, q);

		if (!projectAccessService.isAdmin(currentUser)) {
			List<Long> visibleProjectIds = projectMemberMapper.selectList(
				Wrappers.<ProjectMember>lambdaQuery()
					.select(ProjectMember::getProjectId)
					.eq(ProjectMember::getUserId, currentUser.getId())).stream()
				.map(ProjectMember::getProjectId)
				.toList();
			if (visibleProjectIds.isEmpty()
				|| (projectId != null && !visibleProjectIds.contains(projectId))) {
				return PageResponse.of(List.of(), page, size, 0);
			}
			query.in(Task::getProjectId, visibleProjectIds);
		}

		long totalElements = taskMapper.selectCount(query);
		applySort(query, sort);
		query.orderByAsc(Task::getId)
			.last("LIMIT " + size + " OFFSET " + (long) page * size);
		List<Task> tasks = taskMapper.selectList(query);
		return PageResponse.of(responses(tasks), page, size, totalElements);
	}

	@Transactional(readOnly = true)
	public TaskResponse findById(Authentication authentication, Long taskId) {
		Task task = taskAccessService.requireReadable(authentication, taskId).task();
		return responses(List.of(task)).getFirst();
	}

	@Transactional
	public TaskResponse create(
		Authentication authentication,
		String requestedProjectId,
		String requestedParentId,
		String title,
		String description,
		String requestedAssigneeId,
		TaskStatus status,
		Priority priority,
		LocalDate dueDate) {
		long projectId = parseId(requestedProjectId);
		User currentUser = currentUserService.require(authentication);
		Long assigneeId = parseNullableId(requestedAssigneeId);
		UserWriteLockService.LockedUsers lockedUsers =
			userWriteLockService.lockUsers(currentUser, assigneeId);
		TaskAccessService.ProjectContext context =
			taskAccessService.requireCreatable(authentication, projectId);
		Long parentId = validateParent(context.project(), null, requestedParentId);
		validateAssignee(context.project(), assigneeId, lockedUsers);
		validateDueDate(context.project(), dueDate);

		Task task = new Task();
		task.setProjectId(projectId);
		task.setParentId(parentId);
		task.setAssigneeId(assigneeId);
		task.setCreatorId(context.currentUser().getId());
		task.setTitle(title);
		task.setDescription(description);
		task.setStatus(status);
		task.setPriority(priority);
		task.setDueDate(dueDate);
		taskMapper.insert(task);
		return responses(List.of(taskMapper.selectById(task.getId()))).getFirst();
	}

	@Transactional
	public TaskResponse update(
		Authentication authentication,
		Long taskId,
		String requestedParentId,
		String title,
		String description,
		String requestedAssigneeId,
		TaskStatus status,
		Priority priority,
		LocalDate dueDate) {
		User currentUser = currentUserService.require(authentication);
		Long assigneeId = parseNullableId(requestedAssigneeId);
		UserWriteLockService.LockedUsers lockedUsers =
			userWriteLockService.lockUsers(currentUser, assigneeId);
		TaskAccessService.TaskContext context =
			taskAccessService.requireOwnerWritable(authentication, taskId);
		Long parentId = validateParent(context.project(), taskId, requestedParentId);
		validateAssignee(context.project(), assigneeId, lockedUsers);
		validateDueDate(context.project(), dueDate);

		taskMapper.update(null, Wrappers.<Task>lambdaUpdate()
			.eq(Task::getId, taskId)
			.set(Task::getParentId, parentId)
			.set(Task::getAssigneeId, assigneeId)
			.set(Task::getTitle, title)
			.set(Task::getDescription, description)
			.set(Task::getStatus, status)
			.set(Task::getPriority, priority)
			.set(Task::getDueDate, dueDate));
		return responses(List.of(taskMapper.selectById(taskId))).getFirst();
	}

	public TaskResponse updateStatus(
		Authentication authentication,
		Long taskId,
		TaskStatus status) {
		for (int attempt = 0; attempt < 3; attempt++) {
			try {
				return transactionTemplate.execute(transaction ->
					updateStatusOnce(authentication, taskId, status));
			} catch (StaleTaskAssigneeException exception) {
				if (attempt == 2) {
					throw new BusinessException(ErrorCode.TASK_ASSIGNEE_CHANGED);
				}
			}
		}
		throw new IllegalStateException("Unreachable");
	}

	private TaskResponse updateStatusOnce(
		Authentication authentication,
		Long taskId,
		TaskStatus status) {
		User currentUser = currentUserService.require(authentication);
		Task snapshot = taskId == null ? null : taskMapper.selectById(taskId);
		UserWriteLockService.LockedUsers lockedUsers = userWriteLockService.lockUsers(
			currentUser,
			snapshot == null ? null : snapshot.getAssigneeId());
		TaskAccessService.TaskContext context =
			taskAccessService.requireStatusWritable(authentication, taskId);
		if (!Objects.equals(
			snapshot == null ? null : snapshot.getAssigneeId(),
			context.task().getAssigneeId())) {
			throw new StaleTaskAssigneeException();
		}
		if (isTerminal(context.task().getStatus()) && !isTerminal(status)) {
			validateReopenedAssignee(context.project(), context.task().getAssigneeId(), lockedUsers);
		}
		taskMapper.update(null, Wrappers.<Task>lambdaUpdate()
			.eq(Task::getId, taskId)
			.set(Task::getStatus, status));
		return responses(List.of(taskMapper.selectById(taskId))).getFirst();
	}

	private static final class StaleTaskAssigneeException extends RuntimeException {
	}

	@Transactional
	public void delete(Authentication authentication, Long taskId) {
		taskAccessService.requireOwnerWritable(authentication, taskId);
		if (taskMapper.selectCount(Wrappers.<Task>lambdaQuery()
			.eq(Task::getParentId, taskId)) > 0
			|| taskLogMapper.selectCount(Wrappers.<TaskLog>lambdaQuery()
				.eq(TaskLog::getTaskId, taskId)) > 0
			|| summaryMapper.selectCount(Wrappers.<Summary>lambdaQuery()
				.eq(Summary::getTaskId, taskId)) > 0) {
			throw new BusinessException(ErrorCode.RESOURCE_IN_USE);
		}
		try {
			taskMapper.deleteById(taskId);
		} catch (DataIntegrityViolationException exception) {
			if (isTaskReferenceViolation(exception)) {
				throw new BusinessException(ErrorCode.RESOURCE_IN_USE);
			}
			throw exception;
		}
	}

	private void validateAssignee(
		Project project,
		Long assigneeId,
		UserWriteLockService.LockedUsers lockedUsers) {
		if (assigneeId == null) {
			return;
		}
		User assignee = lockedUsers.user(assigneeId);
		if (assignee == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND);
		}
		if (!isValidAssignee(project, assignee)) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR);
		}
	}

	private void validateReopenedAssignee(
		Project project,
		Long assigneeId,
		UserWriteLockService.LockedUsers lockedUsers) {
		if (assigneeId != null) {
			User assignee = lockedUsers.user(assigneeId);
			if (assignee == null || !isValidAssignee(project, assignee)) {
				throw new BusinessException(ErrorCode.VALIDATION_ERROR);
			}
		}
	}

	private boolean isValidAssignee(Project project, User assignee) {
		return assignee.isActive()
			&& assignee.getSystemRole() == SystemRole.USER
			&& projectMemberMapper.existsByProjectIdAndUserIdForUpdate(
				project.getId(), assignee.getId());
	}

	private Long validateParent(Project project, Long taskId, String requestedParentId) {
		if (requestedParentId == null) {
			return null;
		}
		long parentId = parseId(requestedParentId);
		if (Long.valueOf(parentId).equals(taskId)) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR);
		}
		Set<Long> visited = new HashSet<>();
		Long candidateId = parentId;
		while (candidateId != null) {
			if (!visited.add(candidateId) || candidateId.equals(taskId)) {
				throw new BusinessException(ErrorCode.VALIDATION_ERROR);
			}
			Task candidate = taskMapper.selectByIdForUpdate(candidateId);
			if (candidate == null || !candidate.getProjectId().equals(project.getId())) {
				throw new BusinessException(ErrorCode.NOT_FOUND);
			}
			candidateId = candidate.getParentId();
		}
		return parentId;
	}

	private List<TaskResponse> responses(List<Task> tasks) {
		if (tasks.isEmpty()) {
			return List.of();
		}
		Set<Long> userIds = new HashSet<>();
		for (Task task : tasks) {
			userIds.add(task.getCreatorId());
			if (task.getAssigneeId() != null) {
				userIds.add(task.getAssigneeId());
			}
		}
		Map<Long, User> users = new HashMap<>();
		for (User user : userMapper.selectByIds(userIds)) {
			users.put(user.getId(), user);
		}
		Map<Long, Integer> progressByTaskId = latestProgress(tasks.stream()
			.map(Task::getId)
			.toList());
		return tasks.stream()
			.map(task -> TaskResponse.from(
				task,
				users.get(task.getCreatorId()),
				users.get(task.getAssigneeId()),
				progressByTaskId.getOrDefault(task.getId(), 0)))
			.toList();
	}

	private Map<Long, Integer> latestProgress(List<Long> taskIds) {
		Map<Long, Integer> latest = new HashMap<>();
		for (TaskMapper.LatestProgress progress :
			taskMapper.selectLatestProgressByTaskIds(taskIds)) {
			latest.put(progress.getTaskId(), progress.getProgressPercent());
		}
		return latest;
	}

	private static boolean isTerminal(TaskStatus status) {
		return status == TaskStatus.COMPLETED || status == TaskStatus.CANCELLED;
	}

	private static void validateDueDate(Project project, LocalDate dueDate) {
		if (dueDate != null
			&& ((project.getStartDate() != null && dueDate.isBefore(project.getStartDate()))
				|| (project.getEndDate() != null && dueDate.isAfter(project.getEndDate())))) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR);
		}
	}

	private static long parseId(String value) {
		try {
			return Long.parseLong(value);
		} catch (NumberFormatException exception) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR);
		}
	}

	private static Long parseNullableId(String value) {
		return value == null ? null : parseId(value);
	}

	private static void applySort(LambdaQueryWrapper<Task> query, String sort) {
		String[] parts = sort.split(",", -1);
		if (parts.length != 2 || !(parts[1].equals("asc") || parts[1].equals("desc"))) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR);
		}
		boolean ascending = parts[1].equals("asc");
		switch (parts[0]) {
			case "createdAt" -> query.orderBy(true, ascending, Task::getCreatedAt);
			case "updatedAt" -> query.orderBy(true, ascending, Task::getUpdatedAt);
			case "title" -> query.orderBy(true, ascending, Task::getTitle);
			case "dueDate" -> query.orderBy(true, ascending, Task::getDueDate);
			case "priority" -> query.orderBy(true, ascending, Task::getPriority);
			case "status" -> query.orderBy(true, ascending, Task::getStatus);
			default -> throw new BusinessException(ErrorCode.VALIDATION_ERROR);
		}
	}

	private static boolean isTaskReferenceViolation(DataIntegrityViolationException exception) {
		Throwable cause = exception;
		while (cause != null) {
			String message = cause.getMessage();
			if (message != null
				&& TASK_REFERENCE_CONSTRAINTS.stream().anyMatch(message::contains)) {
				return true;
			}
			cause = cause.getCause();
		}
		return false;
	}
}
