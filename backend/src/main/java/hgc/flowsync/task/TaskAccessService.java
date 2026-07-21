package hgc.flowsync.task;

import java.util.Objects;

import hgc.flowsync.common.error.BusinessException;
import hgc.flowsync.common.error.ErrorCode;
import hgc.flowsync.project.Project;
import hgc.flowsync.project.ProjectAccessService;
import hgc.flowsync.user.CurrentUserService;
import hgc.flowsync.user.User;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskAccessService {

	private final TaskMapper taskMapper;
	private final CurrentUserService currentUserService;
	private final ProjectAccessService projectAccessService;

	public TaskAccessService(
		TaskMapper taskMapper,
		CurrentUserService currentUserService,
		ProjectAccessService projectAccessService) {
		this.taskMapper = taskMapper;
		this.currentUserService = currentUserService;
		this.projectAccessService = projectAccessService;
	}

	public TaskContext requireReadable(Authentication authentication, Long taskId) {
		User currentUser = currentUserService.require(authentication);
		Task task = requireTask(taskId);
		Project project = projectAccessService.requireProject(task.getProjectId());
		if (!projectAccessService.isAdmin(currentUser)
			&& !projectAccessService.isMember(project, currentUser)) {
			throw new BusinessException(ErrorCode.NOT_FOUND);
		}
		return new TaskContext(task, project, currentUser);
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public ProjectContext requireCreatable(Authentication authentication, Long projectId) {
		User currentUser = currentUserService.requireForUpdate(authentication);
		Project project = projectAccessService.requireProjectForUpdate(projectId);
		requireOwner(project, currentUser);
		projectAccessService.requireUnarchived(project);
		return new ProjectContext(project, currentUser);
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public TaskContext requireOwnerWritable(Authentication authentication, Long taskId) {
		TaskContext context = writeContext(authentication, taskId);
		requireOwner(context.project(), context.currentUser());
		projectAccessService.requireUnarchived(context.project());
		return context;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public TaskContext requireStatusWritable(Authentication authentication, Long taskId) {
		TaskContext context = writeContext(authentication, taskId);
		requireOwnerOrAssignee(context);
		projectAccessService.requireUnarchived(context.project());
		return context;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public TaskContext requireTaskLogCreatable(Authentication authentication, Long taskId) {
		TaskContext context = writeContext(authentication, taskId);
		requireOwnerOrAssignee(context);
		projectAccessService.requireUnarchived(context.project());
		return context;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public TaskContext requireTaskLogDeleteContext(Authentication authentication, Long taskId) {
		return writeContext(authentication, taskId);
	}

	private TaskContext writeContext(Authentication authentication, Long taskId) {
		User currentUser = currentUserService.requireForUpdate(authentication);
		Task task = requireTask(taskId);
		Project project = projectAccessService.requireProjectForUpdate(task.getProjectId());
		task = requireTaskForUpdate(taskId);
		if (!Objects.equals(task.getProjectId(), project.getId())
			|| (!projectAccessService.isAdmin(currentUser)
				&& !projectAccessService.isMemberForUpdate(project, currentUser))) {
			throw new BusinessException(ErrorCode.NOT_FOUND);
		}
		return new TaskContext(task, project, currentUser);
	}

	private Task requireTask(Long taskId) {
		Task task = taskId == null ? null : taskMapper.selectById(taskId);
		if (task == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND);
		}
		return task;
	}

	private Task requireTaskForUpdate(Long taskId) {
		Task task = taskMapper.selectByIdForUpdate(taskId);
		if (task == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND);
		}
		return task;
	}

	private void requireOwner(Project project, User currentUser) {
		if (projectAccessService.isAdmin(currentUser)) {
			throw new BusinessException(ErrorCode.FORBIDDEN);
		}
		projectAccessService.requireOwner(project, currentUser);
	}

	private void requireOwnerOrAssignee(TaskContext context) {
		User currentUser = context.currentUser();
		if (projectAccessService.isAdmin(currentUser)
			|| (!projectAccessService.isOwner(context.project(), currentUser)
				&& !Objects.equals(context.task().getAssigneeId(), currentUser.getId()))) {
			throw new BusinessException(ErrorCode.FORBIDDEN);
		}
	}

	public record ProjectContext(Project project, User currentUser) {
	}

	public record TaskContext(Task task, Project project, User currentUser) {
	}
}
