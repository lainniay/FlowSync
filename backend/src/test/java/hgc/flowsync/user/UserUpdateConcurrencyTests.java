package hgc.flowsync.user;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import hgc.flowsync.common.error.BusinessException;
import hgc.flowsync.common.error.ErrorCode;
import hgc.flowsync.project.Priority;
import hgc.flowsync.project.Project;
import hgc.flowsync.project.ProjectMapper;
import hgc.flowsync.project.ProjectMember;
import hgc.flowsync.project.ProjectMemberMapper;
import hgc.flowsync.project.ProjectMemberService;
import hgc.flowsync.project.ProjectStatus;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest
class UserUpdateConcurrencyTests {

	@Autowired
	private UserService userService;
	@Autowired
	private UserMapper userMapper;
	@Autowired
	private PasswordEncoder passwordEncoder;
	@Autowired
	private ProjectMemberService projectMemberService;
	@Autowired
	private ProjectMapper projectMapper;
	@Autowired
	private ProjectMemberMapper projectMemberMapper;
	@MockitoSpyBean
	private UserWriteLockService userWriteLockService;

	@Test
	void concurrentUpdatesCannotRemoveEveryActiveAdmin() throws Exception {
		List<Long> originalAdminIds = userMapper.selectList(Wrappers.<User>lambdaQuery()
			.eq(User::getSystemRole, SystemRole.ADMIN)
			.eq(User::isActive, true)).stream().map(User::getId).toList();
		assertThat(originalAdminIds).isNotEmpty();
		String firstUsername = "cu-" + UUID.randomUUID();
		String secondUsername = "cu-" + UUID.randomUUID();

		try {
			userMapper.update(null, Wrappers.<User>lambdaUpdate()
				.in(User::getId, originalAdminIds)
				.set(User::isActive, false));
			User first = insertAdmin(firstUsername);
			User second = insertAdmin(secondUsername);
			CountDownLatch start = new CountDownLatch(1);
			try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
				Future<ErrorCode> firstResult = executor.submit(() -> deactivate(first, start));
				Future<ErrorCode> secondResult = executor.submit(() -> deactivate(second, start));
				start.countDown();
				assertThat(Arrays.asList(
					firstResult.get(5, TimeUnit.SECONDS),
					secondResult.get(5, TimeUnit.SECONDS)))
					.containsExactlyInAnyOrder(null, ErrorCode.LAST_ADMIN_REQUIRED);
			}

			assertThat(userMapper.selectCount(Wrappers.<User>lambdaQuery()
				.eq(User::getSystemRole, SystemRole.ADMIN)
				.eq(User::isActive, true))).isOne();
		} finally {
			userMapper.update(null, Wrappers.<User>lambdaUpdate()
				.in(User::getId, originalAdminIds)
				.set(User::isActive, true));
			userMapper.delete(Wrappers.<User>lambdaQuery()
				.in(User::getUsername, firstUsername, secondUsername));
		}
	}

	@Test
	void adminCreatedBeforeTheGuardIsLockedPreventsFalseLastAdminRejection() throws Exception {
		List<Long> originalAdminIds = userMapper.selectList(Wrappers.<User>lambdaQuery()
			.eq(User::getSystemRole, SystemRole.ADMIN)
			.eq(User::isActive, true)).stream().map(User::getId).toList();
		assertThat(originalAdminIds).isNotEmpty();
		String firstUsername = "cu-" + UUID.randomUUID();
		String secondUsername = "cu-" + UUID.randomUUID();
		CountDownLatch guardAttempted = new CountDownLatch(1);
		CountDownLatch releaseUpdate = new CountDownLatch(1);
		AtomicReference<Thread> updateThread = new AtomicReference<>();
		UserWriteLockService lockTarget = AopTestUtils.getUltimateTargetObject(userWriteLockService);
		doAnswer(invocation -> {
			if (Thread.currentThread() == updateThread.get()) {
				guardAttempted.countDown();
				assertThat(releaseUpdate.await(5, TimeUnit.SECONDS)).isTrue();
			}
			return invocation.callRealMethod();
		}).when(lockTarget).lockAdminRoleChanges();

		ExecutorService executor = Executors.newSingleThreadExecutor();
		try {
			userMapper.update(null, Wrappers.<User>lambdaUpdate()
				.in(User::getId, originalAdminIds)
				.set(User::isActive, false));
			User first = insertAdmin(firstUsername);
			Future<UserResponse> deactivate = executor.submit(() -> {
				updateThread.set(Thread.currentThread());
				return userService.update(first.getUsername(), first.getId(), first.getDisplayName(), null, null,
					SystemRole.ADMIN, false);
			});
			assertThat(guardAttempted.await(5, TimeUnit.SECONDS)).isTrue();

			UserResponse second = userService.create(
				first.getUsername(),
				secondUsername,
				"test-password",
				"Concurrent Admin",
				SystemRole.ADMIN,
				null,
				null);
			releaseUpdate.countDown();

			assertThat(deactivate.get(5, TimeUnit.SECONDS).active()).isFalse();
			assertThat(userMapper.selectById(Long.valueOf(second.id())).isActive()).isTrue();
		} finally {
			releaseUpdate.countDown();
			executor.shutdownNow();
			assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
			userMapper.update(null, Wrappers.<User>lambdaUpdate()
				.in(User::getId, originalAdminIds)
				.set(User::isActive, true));
			userMapper.delete(Wrappers.<User>lambdaQuery()
				.in(User::getUsername, firstUsername, secondUsername));
		}
	}

	@Test
	void adminWriteRevalidatesActorAfterConcurrentDeactivation() throws Exception {
		User actor = insertUser("Acting Admin", SystemRole.ADMIN);
		User otherAdmin = insertUser("Other Admin", SystemRole.ADMIN);
		String createdUsername = "cu-created-" + UUID.randomUUID();
		CountDownLatch deactivationLocked = new CountDownLatch(1);
		CountDownLatch allowDeactivation = new CountDownLatch(1);
		AtomicReference<Thread> deactivationThread = new AtomicReference<>();
		UserWriteLockService lockTarget = AopTestUtils.getUltimateTargetObject(userWriteLockService);
		doAnswer(invocation -> {
			Object result = invocation.callRealMethod();
			if (Thread.currentThread() == deactivationThread.get()) {
				deactivationLocked.countDown();
				assertThat(allowDeactivation.await(5, TimeUnit.SECONDS)).isTrue();
			}
			return result;
		}).when(lockTarget).lockAdminRoleChanges();

		try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
			Future<?> deactivation = executor.submit(() -> {
				deactivationThread.set(Thread.currentThread());
				return userService.update(
					otherAdmin.getUsername(), actor.getId(), actor.getDisplayName(), null, null,
					SystemRole.ADMIN, false);
			});
			assertThat(deactivationLocked.await(5, TimeUnit.SECONDS)).isTrue();
			Future<?> create = executor.submit(() -> userService.create(
				actor.getUsername(), createdUsername, "test-password", "Created User",
				SystemRole.USER, null, null));
			assertThatThrownBy(() -> create.get(300, TimeUnit.MILLISECONDS))
				.isInstanceOf(TimeoutException.class);
			allowDeactivation.countDown();
			deactivation.get(5, TimeUnit.SECONDS);
			assertThatThrownBy(() -> create.get(5, TimeUnit.SECONDS))
				.isInstanceOf(java.util.concurrent.ExecutionException.class)
				.hasCauseInstanceOf(BusinessException.class)
				.satisfies(exception -> assertThat(((BusinessException) exception.getCause()).code())
					.isEqualTo(ErrorCode.UNAUTHORIZED));
			assertThat(userMapper.selectCount(Wrappers.<User>lambdaQuery()
				.eq(User::getUsername, createdUsername))).isZero();
		} finally {
			allowDeactivation.countDown();
			userMapper.delete(Wrappers.<User>lambdaQuery()
				.in(User::getId, actor.getId(), otherAdmin.getId()));
			userMapper.delete(Wrappers.<User>lambdaQuery().eq(User::getUsername, createdUsername));
		}
	}

	@Test
	void userUpdateAndMemberAddLockUsersInTheSameOrder() throws Exception {
		User target = insertUser("Target", SystemRole.USER);
		User admin = insertUser("Admin", SystemRole.ADMIN);
		User owner = insertUser("Owner", SystemRole.USER);
		Project project = insertProject(owner);
		CountDownLatch updateUsersLocked = new CountDownLatch(1);
		CountDownLatch memberLockAttempted = new CountDownLatch(1);
		CountDownLatch releaseUpdate = new CountDownLatch(1);
		AtomicReference<Thread> updateThread = new AtomicReference<>();
		UserWriteLockService lockTarget = AopTestUtils.getUltimateTargetObject(userWriteLockService);
		doAnswer(invocation -> {
			var users = invocation.callRealMethod();
			if (Thread.currentThread() == updateThread.get()) {
				updateUsersLocked.countDown();
				assertThat(releaseUpdate.await(5, TimeUnit.SECONDS)).isTrue();
			}
			return users;
		}).when(lockTarget).lockUsersById(anyCollection());
		doAnswer(invocation -> {
			memberLockAttempted.countDown();
			return invocation.callRealMethod();
		}).when(lockTarget).lockUsers(any(User.class), any(Long[].class));

		try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
			Future<UserResponse> update = executor.submit(() -> {
				updateThread.set(Thread.currentThread());
				return userService.update(admin.getUsername(), target.getId(), "Updated Target", null, null,
					SystemRole.USER, true);
			});
			assertThat(updateUsersLocked.await(5, TimeUnit.SECONDS)).isTrue();

			Future<?> addMember = executor.submit(() -> projectMemberService.addAll(
				authentication(admin), project.getId(), List.of(target.getId().toString())));
			assertThat(memberLockAttempted.await(5, TimeUnit.SECONDS)).isTrue();
			assertThatThrownBy(() -> addMember.get(300, TimeUnit.MILLISECONDS))
				.isInstanceOf(TimeoutException.class);
			releaseUpdate.countDown();

			assertThat(update.get(5, TimeUnit.SECONDS).displayName()).isEqualTo("Updated Target");
			addMember.get(5, TimeUnit.SECONDS);
			assertThat(projectMemberMapper.existsByProjectIdAndUserId(project.getId(), target.getId()))
				.isTrue();
		} finally {
			releaseUpdate.countDown();
			projectMemberMapper.delete(Wrappers.<ProjectMember>lambdaQuery()
				.eq(ProjectMember::getProjectId, project.getId()));
			projectMapper.deleteById(project.getId());
			userMapper.delete(Wrappers.<User>lambdaQuery()
				.in(User::getId, target.getId(), admin.getId(), owner.getId()));
		}
	}

	private ErrorCode deactivate(User user, CountDownLatch start) throws InterruptedException {
		start.await();
		try {
			userService.update(
				user.getUsername(),
				user.getId(),
				user.getDisplayName(),
				null,
				null,
				SystemRole.ADMIN,
				false);
			return null;
		} catch (BusinessException exception) {
			return exception.code();
		}
	}

	private User insertAdmin(String username) {
		return insertUser(username, "Concurrent Admin", SystemRole.ADMIN);
	}

	private User insertUser(String displayName, SystemRole systemRole) {
		return insertUser("cu-" + UUID.randomUUID(), displayName, systemRole);
	}

	private User insertUser(String username, String displayName, SystemRole systemRole) {
		User user = new User();
		user.setUsername(username);
		user.setPasswordHash(passwordEncoder.encode("test-password"));
		user.setDisplayName(displayName);
		user.setSystemRole(systemRole);
		userMapper.insert(user);
		return userMapper.selectById(user.getId());
	}

	private Project insertProject(User owner) {
		Project project = new Project();
		project.setName("Concurrency Project");
		project.setStatus(ProjectStatus.NOT_STARTED);
		project.setPriority(Priority.MEDIUM);
		project.setStartDate(LocalDate.of(2026, 7, 1));
		project.setEndDate(LocalDate.of(2026, 8, 31));
		project.setOwnerId(owner.getId());
		projectMapper.insert(project);
		ProjectMember ownerMember = new ProjectMember();
		ownerMember.setProjectId(project.getId());
		ownerMember.setUserId(owner.getId());
		projectMemberMapper.insert(ownerMember);
		return project;
	}

	private static Authentication authentication(User user) {
		return UsernamePasswordAuthenticationToken.authenticated(
			user.getUsername(), "", List.of());
	}
}
