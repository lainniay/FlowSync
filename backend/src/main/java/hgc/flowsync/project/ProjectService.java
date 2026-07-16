package hgc.flowsync.project;

import java.time.LocalDate;

import hgc.flowsync.common.error.BusinessException;
import hgc.flowsync.common.error.ErrorCode;
import hgc.flowsync.user.CurrentUserService;
import hgc.flowsync.user.SystemRole;
import hgc.flowsync.user.User;
import hgc.flowsync.user.UserMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {

	private final ProjectMapper projectMapper;
	private final ProjectMemberMapper projectMemberMapper;
	private final UserMapper userMapper;
	private final CurrentUserService currentUserService;

	public ProjectService(
		ProjectMapper projectMapper,
		ProjectMemberMapper projectMemberMapper,
		UserMapper userMapper,
		CurrentUserService currentUserService) {
		this.projectMapper = projectMapper;
		this.projectMemberMapper = projectMemberMapper;
		this.userMapper = userMapper;
		this.currentUserService = currentUserService;
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
		User currentUser = currentUserService.requireForUpdate(authentication);
		if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR);
		}

		User owner = owner(currentUser, requestedOwnerId);
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
		return ProjectResponse.created(projectMapper.selectById(project.getId()), owner);
	}

	private User owner(User currentUser, String requestedOwnerId) {
		if (currentUser.getSystemRole() == SystemRole.USER) {
			if (requestedOwnerId != null) {
				throw new BusinessException(ErrorCode.VALIDATION_ERROR);
			}
			return currentUser;
		}
		if (requestedOwnerId == null) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR);
		}

		long ownerId;
		try {
			ownerId = Long.parseLong(requestedOwnerId);
		} catch (NumberFormatException exception) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR);
		}
		User owner = userMapper.selectOne(Wrappers.<User>lambdaQuery()
			.eq(User::getId, ownerId)
			.last("FOR UPDATE"));
		if (owner == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND);
		}
		if (!owner.isActive() || owner.getSystemRole() != SystemRole.USER) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR);
		}
		return owner;
	}
}
