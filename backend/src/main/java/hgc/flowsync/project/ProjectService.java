package hgc.flowsync.project;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import hgc.flowsync.common.api.PageResponse;
import hgc.flowsync.common.error.BusinessException;
import hgc.flowsync.common.error.ErrorCode;
import hgc.flowsync.common.time.ApiDateTime;
import hgc.flowsync.summary.Summary;
import hgc.flowsync.summary.SummaryMapper;
import hgc.flowsync.task.Task;
import hgc.flowsync.task.TaskLog;
import hgc.flowsync.task.TaskLogMapper;
import hgc.flowsync.task.TaskMapper;
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
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {

	private final ProjectMapper projectMapper;
	private final ProjectMemberMapper projectMemberMapper;
	private final ProjectInvitationMapper projectInvitationMapper;
	private final TaskMapper taskMapper;
	private final TaskLogMapper taskLogMapper;
	private final SummaryMapper summaryMapper;
	private final UserMapper userMapper;
	private final CurrentUserService currentUserService;
	private final ProjectAccessService projectAccessService;
	private final UserWriteLockService userWriteLockService;

	public ProjectService(
		ProjectMapper projectMapper,
		ProjectMemberMapper projectMemberMapper,
		ProjectInvitationMapper projectInvitationMapper,
		TaskMapper taskMapper,
		TaskLogMapper taskLogMapper,
		SummaryMapper summaryMapper,
		UserMapper userMapper,
		CurrentUserService currentUserService,
		ProjectAccessService projectAccessService,
		UserWriteLockService userWriteLockService) {
		this.projectMapper = projectMapper;
		this.projectMemberMapper = projectMemberMapper;
		this.projectInvitationMapper = projectInvitationMapper;
		this.taskMapper = taskMapper;
		this.taskLogMapper = taskLogMapper;
		this.summaryMapper = summaryMapper;
		this.userMapper = userMapper;
		this.currentUserService = currentUserService;
		this.projectAccessService = projectAccessService;
		this.userWriteLockService = userWriteLockService;
	}

	@Transactional(readOnly = true)
	public PageResponse<ProjectResponse> findAll(
		Authentication authentication,
		String q,
		ProjectStatus status,
		String requestedOwnerId,
		boolean archived,
		int page,
		int size,
		String sort) {
		User currentUser = currentUserService.require(authentication);
		Long ownerId = requestedOwnerId == null ? null : parseId(requestedOwnerId);
		LambdaQueryWrapper<Project> query = Wrappers.<Project>lambdaQuery()
			.eq(status != null, Project::getStatus, status)
			.eq(ownerId != null, Project::getOwnerId, ownerId)
			.like(q != null && !q.isEmpty(), Project::getName, q)
			.isNotNull(archived, Project::getArchivedAt)
			.isNull(!archived, Project::getArchivedAt);
		if (!projectAccessService.isAdmin(currentUser)) {
			List<Long> projectIds = projectMemberMapper.selectList(Wrappers.<ProjectMember>lambdaQuery()
				.select(ProjectMember::getProjectId)
				.eq(ProjectMember::getUserId, currentUser.getId())).stream()
				.map(ProjectMember::getProjectId)
				.toList();
			if (projectIds.isEmpty()) {
				return PageResponse.of(List.of(), page, size, 0);
			}
			query.in(Project::getId, projectIds);
		}
		long totalElements = projectMapper.selectCount(query);
		applySort(query, sort);
		query.orderByAsc(Project::getId)
			.last("LIMIT " + size + " OFFSET " + (long) page * size);
		return PageResponse.of(
			responses(projectMapper.selectList(query)),
			page,
			size,
			totalElements);
	}

	@Transactional(readOnly = true)
	public ProjectResponse findById(Authentication authentication, Long projectId) {
		User currentUser = currentUserService.require(authentication);
		Project project = projectAccessService.requireProject(projectId);
		projectAccessService.requireMemberOrAdmin(project, currentUser);
		return response(project);
	}

	@Transactional
	public ProjectResponse create(
		Authentication authentication,
		String name,
		String description,
		ProjectStatus status,
		Priority priority,
		LocalDate startDate,
		LocalDate endDate,
		String requestedOwnerId) {
		if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR);
		}

		User requestedBy = currentUserService.require(authentication);
		Long requestedOwner = requestedOwnerId == null ? null : parseId(requestedOwnerId);
		UserWriteLockService.LockedUsers lockedUsers =
			userWriteLockService.lockUsers(requestedBy, requestedOwner);
		User currentUser = lockedUsers.currentUser();
		Long ownerId;
		if (currentUser.getSystemRole() == SystemRole.USER) {
			if (requestedOwnerId != null) {
				throw new BusinessException(ErrorCode.VALIDATION_ERROR);
			}
			ownerId = currentUser.getId();
		} else {
			if (requestedOwner == null) {
				throw new BusinessException(ErrorCode.VALIDATION_ERROR);
			}
			ownerId = requestedOwner;
		}
		User owner = lockedUsers.user(ownerId);
		if (owner == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND);
		}
		if (!owner.isActive() || owner.getSystemRole() != SystemRole.USER) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR);
		}
		Project project = new Project();
		project.setOwnerId(owner.getId());
		project.setName(name);
		project.setDescription(description);
		project.setStatus(status);
		project.setPriority(priority);
		project.setStartDate(startDate);
		project.setEndDate(endDate);
		projectMapper.insert(project);

		ProjectMember ownerMember = new ProjectMember();
		ownerMember.setProjectId(project.getId());
		ownerMember.setUserId(owner.getId());
		projectMemberMapper.insert(ownerMember);
		return response(projectMapper.selectById(project.getId()));
	}

	@Transactional
	public ProjectResponse update(
		Authentication authentication,
		Long projectId,
		String name,
		String description,
		ProjectStatus status,
		Priority priority,
		LocalDate startDate,
		LocalDate endDate) {
		User currentUser = currentUserService.requireForUpdate(authentication);
		Project project = projectAccessService.requireProjectForUpdate(projectId);
		projectAccessService.requireOwnerOrAdmin(project, currentUser);
		projectAccessService.requireUnarchived(project);
		validateDateRange(startDate, endDate);
		validateTaskDueDates(projectId, startDate, endDate);
		projectMapper.update(null, Wrappers.<Project>lambdaUpdate()
			.eq(Project::getId, projectId)
			.set(Project::getName, name)
			.set(Project::getDescription, description)
			.set(Project::getStatus, status)
			.set(Project::getPriority, priority)
			.set(Project::getStartDate, startDate)
			.set(Project::getEndDate, endDate));
		return response(projectMapper.selectById(projectId));
	}

	@Transactional
	public ProjectResponse transferOwner(
		Authentication authentication,
		Long projectId,
		String requestedOwnerId) {
		long newOwnerId = parseId(requestedOwnerId);
		User requestedBy = currentUserService.require(authentication);
		UserWriteLockService.LockedUsers lockedUsers =
			userWriteLockService.lockUsers(requestedBy, newOwnerId);
		User currentUser = lockedUsers.currentUser();
		User newOwner = lockedUsers.user(newOwnerId);
		if (newOwner == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND);
		}
		if (!newOwner.isActive() || newOwner.getSystemRole() != SystemRole.USER) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR);
		}
		Project project = projectAccessService.requireProjectForUpdate(projectId);
		projectAccessService.requireOwnerOrAdmin(project, currentUser);
		projectAccessService.requireUnarchived(project);
		if (!projectMemberMapper.existsByProjectIdAndUserIdForUpdate(projectId, newOwner.getId())) {
			ProjectMember member = new ProjectMember();
			member.setProjectId(projectId);
			member.setUserId(newOwner.getId());
			projectMemberMapper.insert(member);
			cancelPendingInvitation(projectId, newOwner.getId());
		}
		projectMapper.update(null, Wrappers.<Project>lambdaUpdate()
			.eq(Project::getId, projectId)
			.set(Project::getOwnerId, newOwner.getId()));
		return response(projectMapper.selectById(projectId));
	}

	@Transactional
	public ProjectResponse archive(Authentication authentication, Long projectId) {
		User currentUser = currentUserService.requireForUpdate(authentication);
		Project project = projectAccessService.requireProjectForUpdate(projectId);
		projectAccessService.requireOwnerOrAdmin(project, currentUser);
		projectAccessService.requireUnarchived(project);
		projectMapper.update(null, Wrappers.<Project>lambdaUpdate()
			.eq(Project::getId, projectId)
			.set(Project::getArchivedAt, ApiDateTime.now()));
		return response(projectMapper.selectById(projectId));
	}

	@Transactional
	public ProjectResponse restore(Authentication authentication, Long projectId) {
		User currentUser = currentUserService.requireForUpdate(authentication);
		Project project = projectAccessService.requireProjectForUpdate(projectId);
		projectAccessService.requireOwnerOrAdmin(project, currentUser);
		projectAccessService.requireArchived(project);
		projectMapper.update(null, Wrappers.<Project>lambdaUpdate()
			.eq(Project::getId, projectId)
			.set(Project::getArchivedAt, null));
		return response(projectMapper.selectById(projectId));
	}

	@Transactional
	public void delete(Authentication authentication, Long projectId) {
		User currentUser = currentUserService.requireForUpdate(authentication);
		Project project = projectAccessService.requireProjectForUpdate(projectId);
		projectAccessService.requireOwnerOrAdmin(project, currentUser);
		projectAccessService.requireArchived(project);

		try {
			List<Long> taskIds = taskMapper.selectList(Wrappers.<Task>lambdaQuery()
				.select(Task::getId)
				.eq(Task::getProjectId, projectId)).stream()
				.map(Task::getId)
				.toList();
			summaryMapper.delete(Wrappers.<Summary>lambdaQuery()
				.eq(Summary::getProjectId, projectId));
			if (!taskIds.isEmpty()) {
				taskLogMapper.delete(Wrappers.<TaskLog>lambdaQuery()
					.in(TaskLog::getTaskId, taskIds));
				taskMapper.update(null, Wrappers.<Task>lambdaUpdate()
					.in(Task::getId, taskIds)
					.set(Task::getParentId, null));
			}
			taskMapper.delete(Wrappers.<Task>lambdaQuery()
				.eq(Task::getProjectId, projectId));
			projectInvitationMapper.delete(Wrappers.<ProjectInvitation>lambdaQuery()
				.eq(ProjectInvitation::getProjectId, projectId));
			projectMemberMapper.delete(Wrappers.<ProjectMember>lambdaQuery()
				.eq(ProjectMember::getProjectId, projectId));
			projectMapper.deleteById(projectId);
		} catch (DataIntegrityViolationException exception) {
			throw new BusinessException(ErrorCode.RESOURCE_IN_USE);
		}
	}

	private void cancelPendingInvitation(Long projectId, Long inviteeId) {
		ProjectInvitation invitation = projectInvitationMapper
			.selectByProjectIdAndInviteeIdForUpdate(projectId, inviteeId);
		if (invitation != null && invitation.getStatus() == InvitationStatus.PENDING) {
			invitation.setStatus(InvitationStatus.CANCELLED);
			invitation.setRespondedAt(ApiDateTime.now());
			projectInvitationMapper.updateById(invitation);
		}
	}

	private ProjectResponse response(Project project) {
		return responses(List.of(project)).getFirst();
	}

	private List<ProjectResponse> responses(List<Project> projects) {
		if (projects.isEmpty()) {
			return List.of();
		}
		List<Long> projectIds = projects.stream().map(Project::getId).toList();
		Map<Long, User> owners = userMapper.selectByIds(projects.stream()
			.map(Project::getOwnerId)
			.distinct()
			.toList()).stream().collect(Collectors.toMap(User::getId, owner -> owner));
		Map<Long, Long> memberCounts = projectMemberMapper.countByProjectIds(projectIds).stream()
			.collect(Collectors.toMap(
				ProjectMemberMapper.ProjectMemberCount::getProjectId,
				ProjectMemberMapper.ProjectMemberCount::getMemberCount));
		Map<Long, TaskMapper.ProjectTaskStats> taskStats = taskMapper.selectProjectStats(projectIds).stream()
			.collect(Collectors.toMap(TaskMapper.ProjectTaskStats::getProjectId, stats -> stats));
		return projects.stream().map(project -> {
			TaskMapper.ProjectTaskStats stats = taskStats.get(project.getId());
			return ProjectResponse.from(
				project,
				owners.get(project.getOwnerId()),
				memberCounts.getOrDefault(project.getId(), 0L),
				stats == null ? 0 : stats.getTotal(),
				stats == null ? 0 : stats.getCompleted());
		}).toList();
	}

	private static long parseId(String value) {
		try {
			return Long.parseLong(value);
		} catch (NumberFormatException exception) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR);
		}
	}

	private static void validateDateRange(LocalDate startDate, LocalDate endDate) {
		if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR);
		}
	}

	private void validateTaskDueDates(Long projectId, LocalDate startDate, LocalDate endDate) {
		LambdaQueryWrapper<Task> query = Wrappers.<Task>lambdaQuery()
			.eq(Task::getProjectId, projectId);
		if (startDate != null && endDate != null) {
			query.and(dates -> dates.lt(Task::getDueDate, startDate)
				.or()
				.gt(Task::getDueDate, endDate));
		} else if (startDate != null) {
			query.lt(Task::getDueDate, startDate);
		} else if (endDate != null) {
			query.gt(Task::getDueDate, endDate);
		} else {
			return;
		}
		if (taskMapper.selectCount(query) > 0) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR);
		}
	}

	private static void applySort(LambdaQueryWrapper<Project> query, String sort) {
		String[] parts = sort.split(",", -1);
		boolean ascending = parts[1].equals("asc");
		switch (parts[0]) {
			case "createdAt" -> query.orderBy(true, ascending, Project::getCreatedAt);
			case "updatedAt" -> query.orderBy(true, ascending, Project::getUpdatedAt);
			case "name" -> query.orderBy(true, ascending, Project::getName);
			case "startDate" -> query.orderBy(true, ascending, Project::getStartDate);
			case "endDate" -> query.orderBy(true, ascending, Project::getEndDate);
			case "priority" -> query.orderBy(true, ascending, Project::getPriority);
			default -> throw new BusinessException(ErrorCode.VALIDATION_ERROR);
		}
	}
}
