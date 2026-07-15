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
		UserService userService = new UserService(
			userMapper,
			passwordEncoder,
			mock(SessionRegistry.class));
		when(userMapper.selectCount(any())).thenReturn(0L);
		when(passwordEncoder.encode("initial-test-password")).thenReturn("encoded-password");
		doThrow(new DuplicateKeyException("private database detail"))
			.when(userMapper).insert(any(User.class));

		assertThatThrownBy(() -> userService.create(
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
