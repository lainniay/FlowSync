package hgc.flowsync.project;

import java.util.Objects;

import hgc.flowsync.common.error.BusinessException;
import hgc.flowsync.common.error.ErrorCode;
import hgc.flowsync.user.SystemRole;
import hgc.flowsync.user.User;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Service;

@Service
public class ProjectAccessService {

	private final ProjectMapper projectMapper;
	private final ProjectMemberMapper projectMemberMapper;

	public ProjectAccessService(
		ProjectMapper projectMapper,
		ProjectMemberMapper projectMemberMapper) {
		this.projectMapper = projectMapper;
		this.projectMemberMapper = projectMemberMapper;
	}

	public Project requireProject(Long projectId) {
		return requireProject(projectId, false);
	}

	public Project requireProjectForUpdate(Long projectId) {
		return requireProject(projectId, true);
	}

	private Project requireProject(Long projectId, boolean forUpdate) {
		var query = Wrappers.<Project>lambdaQuery().eq(Project::getId, projectId);
		if (forUpdate) {
			query.last("FOR UPDATE");
		}
		Project project = projectId == null ? null : projectMapper.selectOne(query);
		if (project == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND);
		}
		return project;
	}

	public boolean isAdmin(User user) {
		return user.getSystemRole() == SystemRole.ADMIN;
	}

	public boolean isOwner(Project project, User user) {
		return Objects.equals(project.getOwnerId(), user.getId());
	}

	public boolean isMember(Project project, User user) {
		return projectMemberMapper.existsByProjectIdAndUserId(project.getId(), user.getId());
	}

	public boolean isMemberForUpdate(Project project, User user) {
		return projectMemberMapper.existsByProjectIdAndUserIdForUpdate(
			project.getId(), user.getId());
	}

	public void requireOwner(Project project, User user) {
		if (!isOwner(project, user)) {
			throw new BusinessException(ErrorCode.FORBIDDEN);
		}
	}

	public void requireMemberOrAdmin(Project project, User user) {
		if (!isAdmin(user) && !isMember(project, user)) {
			throw new BusinessException(ErrorCode.NOT_FOUND);
		}
	}

	public void requireOwnerOrAdmin(Project project, User user) {
		if (!isAdmin(user) && !isOwner(project, user)) {
			throw new BusinessException(ErrorCode.FORBIDDEN);
		}
	}

	public void requireUnarchived(Project project) {
		if (project.getArchivedAt() != null) {
			throw new BusinessException(ErrorCode.PROJECT_ARCHIVED);
		}
	}

	public void requireArchived(Project project) {
		if (project.getArchivedAt() == null) {
			throw new BusinessException(ErrorCode.PROJECT_NOT_ARCHIVED);
		}
	}
}
