package hgc.flowsync.project;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import hgc.flowsync.common.error.BusinessException;
import hgc.flowsync.common.error.ErrorCode;
import hgc.flowsync.user.CurrentUserService;
import hgc.flowsync.user.SystemRole;
import hgc.flowsync.user.User;
import hgc.flowsync.user.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class ProjectAccessServiceTests {

	@Autowired
	private ProjectAccessService projectAccessService;
	@Autowired
	private CurrentUserService currentUserService;
	@Autowired
	private UserMapper userMapper;
	@Autowired
	private ProjectMapper projectMapper;
	@Autowired
	private ProjectMemberMapper projectMemberMapper;

	private User admin;
	private User owner;
	private User member;
	private User outsider;
	private User inactiveUser;
	private Project project;
	private Project archivedProject;

	@BeforeEach
	void createAccessMatrix() {
		admin = insertUser(SystemRole.ADMIN, true);
		owner = insertUser(SystemRole.USER, true);
		member = insertUser(SystemRole.USER, true);
		outsider = insertUser(SystemRole.USER, true);
		inactiveUser = insertUser(SystemRole.USER, false);
		project = insertProject(null);
		archivedProject = insertProject(LocalDateTime.of(2026, 7, 16, 12, 0));
		insertMember(project, owner);
		insertMember(project, member);
	}

	@Test
	void resolvesCurrentUserAndProjectOrUsesContractErrors() {
		assertThat(currentUserService.require(authentication(owner)).getId()).isEqualTo(owner.getId());
		assertThat(currentUserService.requireForUpdate(authentication(owner)).getId())
			.isEqualTo(owner.getId());
		assertBusinessError(
			() -> currentUserService.require(authentication(inactiveUser)),
			ErrorCode.UNAUTHORIZED);
		assertBusinessError(
			() -> currentUserService.require(authentication("missing-" + UUID.randomUUID())),
			ErrorCode.UNAUTHORIZED);
		assertBusinessError(() -> currentUserService.require(null), ErrorCode.UNAUTHORIZED);

		assertThat(projectAccessService.requireProject(project.getId()).getId()).isEqualTo(project.getId());
		assertBusinessError(() -> projectAccessService.requireProject(Long.MAX_VALUE), ErrorCode.NOT_FOUND);
		assertBusinessError(() -> projectAccessService.requireProject(null), ErrorCode.NOT_FOUND);
	}

	@Test
	void enforcesAdminOwnerMemberAndArchiveMatrix() {
		assertThat(projectAccessService.isAdmin(admin)).isTrue();
		assertThat(projectAccessService.isAdmin(owner)).isFalse();
		assertThat(projectAccessService.isOwner(project, owner)).isTrue();
		assertThat(projectAccessService.isOwner(project, admin)).isFalse();
		assertThat(projectAccessService.isOwner(project, member)).isFalse();
		assertThat(projectAccessService.isMember(project, owner)).isTrue();
		assertThat(projectAccessService.isMember(project, member)).isTrue();
		assertThat(projectAccessService.isMember(project, admin)).isFalse();
		assertThat(projectAccessService.isMember(project, outsider)).isFalse();

		assertThatCode(() -> projectAccessService.requireOwner(project, owner)).doesNotThrowAnyException();
		forbidden(() -> projectAccessService.requireOwner(project, admin));
		forbidden(() -> projectAccessService.requireOwner(project, member));
		forbidden(() -> projectAccessService.requireOwner(project, outsider));

		assertThatCode(() -> projectAccessService.requireMemberOrAdmin(project, owner))
			.doesNotThrowAnyException();
		assertThatCode(() -> projectAccessService.requireMemberOrAdmin(project, member))
			.doesNotThrowAnyException();
		assertThatCode(() -> projectAccessService.requireMemberOrAdmin(project, admin))
			.doesNotThrowAnyException();
		forbidden(() -> projectAccessService.requireMemberOrAdmin(project, outsider));

		assertThatCode(() -> projectAccessService.requireOwnerOrAdmin(project, owner))
			.doesNotThrowAnyException();
		assertThatCode(() -> projectAccessService.requireOwnerOrAdmin(project, admin))
			.doesNotThrowAnyException();
		forbidden(() -> projectAccessService.requireOwnerOrAdmin(project, member));
		forbidden(() -> projectAccessService.requireOwnerOrAdmin(project, outsider));

		assertThatCode(() -> projectAccessService.requireUnarchived(project)).doesNotThrowAnyException();
		assertBusinessError(() -> projectAccessService.requireArchived(project), ErrorCode.PROJECT_NOT_ARCHIVED);
		assertThatCode(() -> projectAccessService.requireArchived(archivedProject)).doesNotThrowAnyException();
		assertBusinessError(
			() -> projectAccessService.requireUnarchived(archivedProject),
			ErrorCode.PROJECT_ARCHIVED);
	}

	private User insertUser(SystemRole role, boolean active) {
		User user = new User();
		user.setUsername("access-" + UUID.randomUUID());
		user.setPasswordHash("test-password-hash");
		user.setDisplayName("Access User");
		user.setSystemRole(role);
		user.setActive(active);
		userMapper.insert(user);
		return user;
	}

	private Project insertProject(LocalDateTime archivedAt) {
		Project value = new Project();
		value.setOwnerId(owner.getId());
		value.setName("Access Project " + UUID.randomUUID());
		value.setStatus(ProjectStatus.NOT_STARTED);
		value.setPriority(Priority.MEDIUM);
		value.setArchivedAt(archivedAt);
		projectMapper.insert(value);
		return value;
	}

	private void insertMember(Project value, User user) {
		ProjectMember projectMember = new ProjectMember();
		projectMember.setProjectId(value.getId());
		projectMember.setUserId(user.getId());
		projectMemberMapper.insert(projectMember);
	}

	private static Authentication authentication(User user) {
		return authentication(user.getUsername());
	}

	private static Authentication authentication(String username) {
		return UsernamePasswordAuthenticationToken.authenticated(username, "", List.of());
	}

	private static void forbidden(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
		assertBusinessError(action, ErrorCode.FORBIDDEN);
	}

	private static void assertBusinessError(
		org.assertj.core.api.ThrowableAssert.ThrowingCallable action,
		ErrorCode code) {
		assertThatThrownBy(action)
			.isInstanceOfSatisfying(BusinessException.class,
				exception -> assertThat(exception.code()).isEqualTo(code));
	}
}
