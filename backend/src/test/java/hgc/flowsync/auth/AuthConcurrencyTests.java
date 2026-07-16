package hgc.flowsync.auth;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import hgc.flowsync.user.SystemRole;
import hgc.flowsync.user.User;
import hgc.flowsync.user.UserMapper;
import hgc.flowsync.user.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.dao.DataAccessResourceFailureException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class AuthConcurrencyTests {

	private final AuthService authService;
	private final UserMapper userMapper;
	private final PasswordEncoder passwordEncoder;
	private Long createdUserId;

	@MockitoSpyBean
	private SessionRegistry sessionRegistry;
	@MockitoSpyBean
	private UserService userService;

	@AfterEach
	void deleteCreatedUser() {
		if (createdUserId != null) {
			userMapper.deleteById(createdUserId);
		}
	}

	@Autowired
	AuthConcurrencyTests(
		AuthService authService,
		UserService userService,
		UserMapper userMapper,
		PasswordEncoder passwordEncoder) {
		this.authService = authService;
		this.userService = userService;
		this.userMapper = userMapper;
		this.passwordEncoder = passwordEncoder;
	}

	@Test
	void passwordResetWaitsForConcurrentLoginRegistration() throws Exception {
		User user = insertUser();
		CountDownLatch registrationReached = new CountDownLatch(1);
		CountDownLatch allowRegistration = new CountDownLatch(1);
		CountDownLatch invalidationReached = new CountDownLatch(1);
		MockHttpServletRequest request = new MockHttpServletRequest();

		doAnswer(invocation -> {
			registrationReached.countDown();
			assertThat(allowRegistration.await(5, TimeUnit.SECONDS)).isTrue();
			return invocation.callRealMethod();
		}).when(sessionRegistry).registerNewSession(anyString(), eq(user.getUsername()));
		doAnswer(invocation -> {
			invalidationReached.countDown();
			return invocation.callRealMethod();
		}).when(sessionRegistry).getAllSessions(eq(user.getUsername()), anyBoolean());

		try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
			Future<?> login = executor.submit(() -> authService.login(
				user.getUsername(),
				"test-password",
				request,
				new MockHttpServletResponse()));
			assertThat(registrationReached.await(5, TimeUnit.SECONDS)).isTrue();

			Future<?> reset = executor.submit(() ->
				userService.resetPassword(user.getId(), "reset-test-password"));
			try {
				assertThat(invalidationReached.await(300, TimeUnit.MILLISECONDS)).isFalse();
			} finally {
				allowRegistration.countDown();
			}

			login.get(5, TimeUnit.SECONDS);
			reset.get(5, TimeUnit.SECONDS);
		}

		assertThat(sessionRegistry.getSessionInformation(request.getSession().getId()).isExpired()).isTrue();
	}

	@Test
	void profileUpdateRollsBackWhenResponseReadBackFails() {
		User user = insertUser();
		doAnswer(invocation -> {
			invocation.callRealMethod();
			throw new DataAccessResourceFailureException("forced response read-back failure");
		}).when(userService).updateProfile(any(User.class), eq("Changed Name"), isNull(), isNull());

		assertThatThrownBy(() -> authService.updateProfile(
			UsernamePasswordAuthenticationToken.authenticated(user.getUsername(), "", java.util.List.of()),
			"Changed Name",
			null,
			null))
			.isInstanceOf(DataAccessResourceFailureException.class);

		assertThat(userMapper.selectById(user.getId()).getDisplayName()).isEqualTo("Concurrent User");
	}

	private User insertUser() {
		User user = new User();
		user.setUsername("concurrent-" + UUID.randomUUID());
		user.setPasswordHash(passwordEncoder.encode("test-password"));
		user.setDisplayName("Concurrent User");
		user.setSystemRole(SystemRole.USER);
		userMapper.insert(user);
		createdUserId = user.getId();
		return user;
	}
}
