package hgc.flowsync.auth;

import java.util.UUID;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import hgc.flowsync.user.SystemRole;
import hgc.flowsync.user.DatabaseUserDetailsService;
import hgc.flowsync.user.User;
import hgc.flowsync.user.UserMapper;
import hgc.flowsync.user.UserService;
import hgc.flowsync.common.error.BusinessException;
import hgc.flowsync.common.error.ErrorCode;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class AuthConcurrencyTests {

	private final AuthService authService;
	private final UserMapper userMapper;
	private final PasswordEncoder passwordEncoder;
	private final PlatformTransactionManager transactionManager;
	private Long createdUserId;

	@MockitoSpyBean
	private SessionRegistry sessionRegistry;
	@MockitoSpyBean
	private UserService userService;
	@MockitoSpyBean
	private DatabaseUserDetailsService databaseUserDetailsService;

	@AfterEach
	void deleteCreatedUser() {
		sessionRegistry.getAllPrincipals().forEach(principal ->
			sessionRegistry.getAllSessions(principal, true).forEach(session ->
				sessionRegistry.removeSessionInformation(session.getSessionId())));
		if (createdUserId != null) {
			userMapper.deleteById(createdUserId);
		}
	}

	@Autowired
	AuthConcurrencyTests(
		AuthService authService,
		UserService userService,
		UserMapper userMapper,
		PasswordEncoder passwordEncoder,
		PlatformTransactionManager transactionManager) {
		this.authService = authService;
		this.userService = userService;
		this.userMapper = userMapper;
		this.passwordEncoder = passwordEncoder;
		this.transactionManager = transactionManager;
	}

	@Test
	void passwordResetWaitsForConcurrentLoginRegistration() throws Exception {
		User user = insertUser();
		CountDownLatch registrationReached = new CountDownLatch(1);
		CountDownLatch allowRegistration = new CountDownLatch(1);
		CountDownLatch invalidationReached = new CountDownLatch(1);
		MockHttpServletRequest request = new MockHttpServletRequest();
		User admin = userMapper.selectOne(Wrappers.<User>lambdaQuery()
			.eq(User::getSystemRole, SystemRole.ADMIN)
			.eq(User::isActive, true)
			.last("LIMIT 1"));

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
				userService.resetPassword(admin.getUsername(), user.getId(), "reset-test-password"));
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
	void loginAuthenticatesFromTheLockedUserWithoutReloadingIt() {
		User user = insertUser();

		authService.login(
			user.getUsername(),
			"test-password",
			new MockHttpServletRequest(),
			new MockHttpServletResponse());

		verify(databaseUserDetailsService, never()).loadUserByUsername(anyString());
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

	@Test
	void passwordAndSessionRemainValidWhenTransactionRollsBack() {
		User user = insertUser();
		MockHttpServletRequest request = new MockHttpServletRequest();
		authService.login(
			user.getUsername(),
			"test-password",
			request,
			new MockHttpServletResponse());
		doAnswer(invocation -> {
			invocation.callRealMethod();
			throw new DataAccessResourceFailureException("forced password failure");
		}).when(userService).updatePassword(any(User.class), eq("reset-test-password"));

		assertThatThrownBy(() -> authService.changePassword(
			UsernamePasswordAuthenticationToken.authenticated(user.getUsername(), "", java.util.List.of()),
			"test-password",
			"reset-test-password"))
			.isInstanceOf(DataAccessResourceFailureException.class);

		assertThat(passwordEncoder.matches(
			"test-password", userMapper.selectById(user.getId()).getPasswordHash())).isTrue();
		assertThat(sessionRegistry.getSessionInformation(request.getSession().getId()).isExpired()).isFalse();
	}

	@Test
	void profileUpdateCannotPassConcurrentDeactivation() throws Exception {
		User user = insertUser();
		CountDownLatch userLocked = new CountDownLatch(1);
		CountDownLatch allowDeactivation = new CountDownLatch(1);
		try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
			Future<?> deactivation = executor.submit(() ->
				new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
					userMapper.selectOne(Wrappers.<User>lambdaQuery()
						.eq(User::getId, user.getId())
						.last("FOR UPDATE"));
					userLocked.countDown();
					try {
						assertThat(allowDeactivation.await(5, TimeUnit.SECONDS)).isTrue();
					} catch (InterruptedException exception) {
						Thread.currentThread().interrupt();
						throw new AssertionError(exception);
					}
					userMapper.update(null, Wrappers.<User>lambdaUpdate()
						.eq(User::getId, user.getId())
						.set(User::isActive, false));
				}));
			assertThat(userLocked.await(5, TimeUnit.SECONDS)).isTrue();
			Future<?> profile = executor.submit(() -> authService.updateProfile(
				UsernamePasswordAuthenticationToken.authenticated(user.getUsername(), "", List.of()),
				"Changed Name", null, null));
			assertThatThrownBy(() -> profile.get(300, TimeUnit.MILLISECONDS))
				.isInstanceOf(java.util.concurrent.TimeoutException.class);
			allowDeactivation.countDown();
			deactivation.get(5, TimeUnit.SECONDS);
			assertThatThrownBy(() -> profile.get(5, TimeUnit.SECONDS))
				.isInstanceOf(java.util.concurrent.ExecutionException.class)
				.satisfies(exception -> {
					Throwable cause = exception.getCause();
					assertThat(cause).isInstanceOf(BusinessException.class);
					assertThat(((BusinessException) cause).code()).isEqualTo(ErrorCode.UNAUTHORIZED);
				});
		}
		assertThat(userMapper.selectById(user.getId()).getDisplayName()).isEqualTo("Concurrent User");
	}

	@Test
	void sessionCreatedAfterCommitIsNotInvalidated() {
		User user = insertUser();
		sessionRegistry.registerNewSession("old-session", user.getUsername());
		new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					sessionRegistry.registerNewSession("new-session", user.getUsername());
				}
			});
			userService.updatePassword(user, "new-test-password");
		});

		assertThat(sessionRegistry.getSessionInformation("old-session").isExpired()).isTrue();
		assertThat(sessionRegistry.getSessionInformation("new-session").isExpired()).isFalse();
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
