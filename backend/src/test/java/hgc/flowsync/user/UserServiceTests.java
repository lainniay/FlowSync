package hgc.flowsync.user;

import hgc.flowsync.common.error.BusinessException;
import hgc.flowsync.common.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserServiceTests {

	@Test
	void concurrentUsernameConflictUsesBusinessError() {
		UserMapper userMapper = mock(UserMapper.class);
		PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
		UserWriteLockService userWriteLockService = mock(UserWriteLockService.class);
		UserService userService = new UserService(
			userMapper,
			passwordEncoder,
			mock(SessionRegistry.class),
			mock(hgc.flowsync.project.ProjectMapper.class),
			mock(hgc.flowsync.project.ProjectMemberMapper.class),
			mock(hgc.flowsync.project.ProjectInvitationMapper.class),
			mock(hgc.flowsync.task.TaskMapper.class),
			userWriteLockService);
		User actingAdmin = new User();
		actingAdmin.setId(1L);
		actingAdmin.setUsername("admin");
		actingAdmin.setSystemRole(SystemRole.ADMIN);
		actingAdmin.setActive(true);
		when(userMapper.selectOne(any())).thenReturn(actingAdmin);
		when(userWriteLockService.lockUsersById(any())).thenReturn(java.util.Map.of(1L, actingAdmin));
		when(userMapper.selectCount(any())).thenReturn(0L);
		when(passwordEncoder.encode("initial-test-password")).thenReturn("encoded-password");
		doThrow(new DuplicateKeyException("private database detail"))
			.when(userMapper).insert(any(User.class));

		assertThatThrownBy(() -> userService.create(
			"admin",
			"duplicate",
			"initial-test-password",
			"Duplicate User",
			SystemRole.USER,
			null,
			null))
			.isInstanceOfSatisfying(BusinessException.class,
				exception -> org.assertj.core.api.Assertions.assertThat(exception.code())
					.isEqualTo(ErrorCode.USERNAME_ALREADY_EXISTS))
			.hasMessage("Username already exists.")
			.hasMessageNotContaining("private database detail");
	}
}
