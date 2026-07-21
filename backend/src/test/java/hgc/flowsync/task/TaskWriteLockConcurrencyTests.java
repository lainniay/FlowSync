package hgc.flowsync.task;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import hgc.flowsync.common.error.BusinessException;
import hgc.flowsync.common.error.ErrorCode;
import hgc.flowsync.project.Priority;
import hgc.flowsync.project.Project;
import hgc.flowsync.project.ProjectAccessService;
import hgc.flowsync.project.ProjectMapper;
import hgc.flowsync.project.ProjectMember;
import hgc.flowsync.project.ProjectMemberMapper;
import hgc.flowsync.project.ProjectMemberService;
import hgc.flowsync.project.ProjectService;
import hgc.flowsync.project.ProjectStatus;
import hgc.flowsync.user.SystemRole;
import hgc.flowsync.user.User;
import hgc.flowsync.user.UserMapper;
import hgc.flowsync.user.UserWriteLockService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest
class TaskWriteLockConcurrencyTests {

	@Autowired
	private TaskService taskService;
	@Autowired
	private TaskLogService taskLogService;
	@Autowired
	private ProjectService projectService;
	@Autowired
	private ProjectMemberService projectMemberService;
	@Autowired
	private TaskMapper taskMapper;
	@Autowired
	private TaskLogMapper taskLogMapper;
	@Autowired
	private ProjectMapper projectMapper;
	@Autowired
	private ProjectMemberMapper projectMemberMapper;
	@Autowired
	private UserMapper userMapper;
	@Autowired
	private PlatformTransactionManager transactionManager;
	@MockitoSpyBean
	private UserWriteLockService userWriteLockService;
	@MockitoSpyBean
	private ProjectAccessService projectAccessService;

	private final List<Long> userIds = new ArrayList<>();
	private Long projectId;

	@AfterEach
	void deleteCreatedRecords() {
		if (projectId != null) {
			List<Long> taskIds = taskMapper.selectList(Wrappers.<Task>lambdaQuery()
				.select(Task::getId)
				.eq(Task::getProjectId, projectId)).stream()
				.map(Task::getId)
				.toList();
			if (!taskIds.isEmpty()) {
				taskLogMapper.delete(Wrappers.<TaskLog>lambdaQuery()
					.in(TaskLog::getTaskId, taskIds));
				taskMapper.update(null, Wrappers.<Task>lambdaUpdate()
					.in(Task::getId, taskIds)
					.set(Task::getParentId, null));
			}
			taskMapper.delete(Wrappers.<Task>lambdaQuery().eq(Task::getProjectId, projectId));
			projectMemberMapper.delete(Wrappers.<ProjectMember>lambdaQuery()
				.eq(ProjectMember::getProjectId, projectId));
			projectMapper.deleteById(projectId);
		}
		if (!userIds.isEmpty()) {
			userMapper.deleteBatchIds(userIds);
		}
	}

	@Test
	void staleSnapshotCannotCreateParentCycle() throws Exception {
		User owner = insertUser("Owner");
		Project project = insertProject(owner);
		Task first = insertTask(project, owner, owner);
		Task second = insertTask(project, owner, owner);
		CountDownLatch snapshotRead = new CountDownLatch(1);
		CountDownLatch releaseUpdate = new CountDownLatch(1);
		ExecutorService executor = Executors.newSingleThreadExecutor();
		try {
			Future<?> staleUpdate = executor.submit(() -> new TransactionTemplate(transactionManager)
				.executeWithoutResult(status -> {
					taskMapper.selectById(first.getId());
					snapshotRead.countDown();
					await(releaseUpdate);
					taskService.update(authentication(owner), second.getId(), first.getId().toString(),
						"Second", null, owner.getId().toString(), TaskStatus.NOT_STARTED,
						Priority.MEDIUM, LocalDate.of(2026, 7, 15));
				}));
			assertThat(snapshotRead.await(5, TimeUnit.SECONDS)).isTrue();

			taskService.update(authentication(owner), first.getId(), second.getId().toString(),
				"First", null, owner.getId().toString(), TaskStatus.NOT_STARTED,
				Priority.MEDIUM, LocalDate.of(2026, 7, 15));
			releaseUpdate.countDown();

			assertBusinessFailure(staleUpdate, ErrorCode.VALIDATION_ERROR);
			assertThat(taskMapper.selectById(first.getId()).getParentId()).isEqualTo(second.getId());
			assertThat(taskMapper.selectById(second.getId()).getParentId()).isNull();
		} finally {
			releaseUpdate.countDown();
			shutdown(executor);
		}
	}

	@Test
	void memberRemovalCommittedBeforeTaskLogWriteIsObserved() throws Exception {
		User owner = insertUser("Owner");
		User member = insertUser("Member");
		User admin = insertUser("Admin", SystemRole.ADMIN);
		Project project = insertProject(owner);
		addMember(project, member);
		Task task = insertTask(project, owner, member);
		task.setStatus(TaskStatus.COMPLETED);
		taskMapper.updateById(task);
		CountDownLatch snapshotRead = new CountDownLatch(1);
		CountDownLatch releaseWrite = new CountDownLatch(1);
		AtomicReference<Thread> writerThread = new AtomicReference<>();
		ProjectAccessService accessTarget = AopTestUtils.getUltimateTargetObject(projectAccessService);
		doAnswer(invocation -> {
			if (Thread.currentThread() == writerThread.get()) {
				snapshotRead.countDown();
				await(releaseWrite);
			}
			return invocation.callRealMethod();
		}).when(accessTarget).requireProjectForUpdate(project.getId());

		ExecutorService executor = Executors.newSingleThreadExecutor();
		try {
			Future<?> write = executor.submit(() -> {
				writerThread.set(Thread.currentThread());
				return taskLogService.create(authentication(member), task.getId(), 100, "Finished");
			});
			assertThat(snapshotRead.await(5, TimeUnit.SECONDS)).isTrue();
			projectMemberService.remove(authentication(admin), project.getId(), member.getId());
			releaseWrite.countDown();

			assertBusinessFailure(write, ErrorCode.NOT_FOUND);
			assertThat(taskLogMapper.selectCount(Wrappers.<TaskLog>lambdaQuery()
				.eq(TaskLog::getTaskId, task.getId()))).isZero();
		} finally {
			releaseWrite.countDown();
			shutdown(executor);
		}
	}

	@Test
	void ownerTransferAndTaskCreateUseTheSameUserBeforeProjectLockOrder() throws Exception {
		User owner = insertUser("Owner");
		User newOwner = insertUser("New Owner");
		User admin = insertUser("Admin", SystemRole.ADMIN);
		Project project = insertProject(owner);
		addMember(project, newOwner);
		CountDownLatch taskUsersLocked = new CountDownLatch(1);
		CountDownLatch transferLockAttempted = new CountDownLatch(1);
		CountDownLatch releaseTask = new CountDownLatch(1);
		UserWriteLockService lockTarget = AopTestUtils.getUltimateTargetObject(userWriteLockService);
		doAnswer(invocation -> {
			User currentUser = invocation.getArgument(0, User.class);
			if (currentUser.getUsername().equals(owner.getUsername())) {
				UserWriteLockService.LockedUsers users =
					(UserWriteLockService.LockedUsers) invocation.callRealMethod();
				taskUsersLocked.countDown();
				await(releaseTask);
				return users;
			}
			transferLockAttempted.countDown();
			return invocation.callRealMethod();
		}).when(lockTarget).lockUsers(any(User.class), any(Long[].class));

		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<TaskResponse> create = executor.submit(() -> taskService.create(
				authentication(owner), project.getId().toString(), null, "Created Concurrently",
				null, newOwner.getId().toString(), TaskStatus.NOT_STARTED, Priority.MEDIUM,
				LocalDate.of(2026, 7, 15)));
			assertThat(taskUsersLocked.await(5, TimeUnit.SECONDS)).isTrue();

			Future<?> transfer = executor.submit(() -> projectService.transferOwner(
				authentication(admin), project.getId(), newOwner.getId().toString()));
			assertThat(transferLockAttempted.await(5, TimeUnit.SECONDS)).isTrue();
			assertThatThrownBy(() -> transfer.get(500, TimeUnit.MILLISECONDS))
				.isInstanceOf(TimeoutException.class);
			releaseTask.countDown();

			assertThat(create.get(10, TimeUnit.SECONDS).assignee().id())
				.isEqualTo(newOwner.getId().toString());
			transfer.get(10, TimeUnit.SECONDS);
			assertThat(projectMapper.selectById(project.getId()).getOwnerId()).isEqualTo(newOwner.getId());
		} finally {
			releaseTask.countDown();
			shutdown(executor);
		}
	}

	@Test
	void ownerReopenRetriesWhenAssigneeChangesBeforeLocks() throws Exception {
		User owner = insertUser("Owner");
		User firstAssignee = insertUser("First Assignee");
		User secondAssignee = insertUser("Second Assignee");
		Project project = insertProject(owner);
		addMember(project, firstAssignee);
		addMember(project, secondAssignee);
		Task task = insertTask(project, owner, firstAssignee);
		task.setStatus(TaskStatus.COMPLETED);
		taskMapper.updateById(task);
		CountDownLatch staleSnapshotRead = new CountDownLatch(1);
		CountDownLatch releaseReopen = new CountDownLatch(1);
		AtomicBoolean pauseFirstOwnerLock = new AtomicBoolean(true);
		UserWriteLockService lockTarget = AopTestUtils.getUltimateTargetObject(userWriteLockService);
		doAnswer(invocation -> {
			User currentUser = invocation.getArgument(0, User.class);
			if (currentUser.getId().equals(owner.getId())
				&& pauseFirstOwnerLock.compareAndSet(true, false)) {
				staleSnapshotRead.countDown();
				await(releaseReopen);
			}
			return invocation.callRealMethod();
		}).when(lockTarget).lockUsers(any(User.class), any(Long[].class));

		ExecutorService executor = Executors.newSingleThreadExecutor();
		try {
			Future<TaskResponse> reopen = executor.submit(() -> taskService.updateStatus(
				authentication(owner), task.getId(), TaskStatus.IN_PROGRESS));
			assertThat(staleSnapshotRead.await(5, TimeUnit.SECONDS)).isTrue();

			taskService.update(authentication(owner), task.getId(), null, task.getTitle(), null,
				secondAssignee.getId().toString(), TaskStatus.COMPLETED, Priority.MEDIUM,
				LocalDate.of(2026, 7, 15));
			releaseReopen.countDown();

			TaskResponse response = reopen.get(10, TimeUnit.SECONDS);
			assertThat(response.status()).isEqualTo(TaskStatus.IN_PROGRESS);
			assertThat(response.assignee().id()).isEqualTo(secondAssignee.getId().toString());
		} finally {
			releaseReopen.countDown();
			shutdown(executor);
		}
	}

	@Test
	void statusUpdateReturnsConflictWhenAssigneeKeepsChangingAcrossRetries() {
		User owner = insertUser("Owner");
		User firstAssignee = insertUser("First Assignee");
		User secondAssignee = insertUser("Second Assignee");
		Project project = insertProject(owner);
		addMember(project, firstAssignee);
		addMember(project, secondAssignee);
		Task task = insertTask(project, owner, firstAssignee);
		AtomicInteger attempts = new AtomicInteger();
		UserWriteLockService lockTarget = AopTestUtils.getUltimateTargetObject(userWriteLockService);
		doAnswer(invocation -> {
			if (invocation.getArgument(0, User.class).getId().equals(owner.getId())) {
				attempts.incrementAndGet();
				taskMapper.update(null, Wrappers.<Task>lambdaUpdate()
					.eq(Task::getId, task.getId())
					.set(Task::getAssigneeId, secondAssignee.getId()));
			}
			return invocation.callRealMethod();
		}).when(lockTarget).lockUsers(any(User.class), any(Long[].class));

		assertThatThrownBy(() -> taskService.updateStatus(
			authentication(owner), task.getId(), TaskStatus.COMPLETED))
			.isInstanceOfSatisfying(BusinessException.class, exception -> {
				assertThat(exception.code()).isEqualTo(ErrorCode.TASK_ASSIGNEE_CHANGED);
				assertThat(exception.code().status().value()).isEqualTo(409);
			});
		assertThat(attempts).hasValue(3);
		assertThat(taskMapper.selectById(task.getId()).getAssigneeId()).isEqualTo(firstAssignee.getId());
	}

	@Test
	void ownerFullUpdateAndAssigneeStatusWriteCompleteWithoutDeadlock() throws Exception {
		User owner = insertUser("Owner");
		User assignee = insertUser("Assignee");
		Project project = insertProject(owner);
		addMember(project, assignee);
		Task task = insertTask(project, owner, assignee);
		CountDownLatch assigneeUsersLocked = new CountDownLatch(1);
		CountDownLatch ownerLockAttempted = new CountDownLatch(1);
		CountDownLatch releaseAssignee = new CountDownLatch(1);

		UserWriteLockService lockTarget = AopTestUtils.getUltimateTargetObject(userWriteLockService);
		doAnswer(invocation -> {
			User currentUser = invocation.getArgument(0, User.class);
			if (currentUser.getUsername().equals(assignee.getUsername())) {
				UserWriteLockService.LockedUsers users =
					(UserWriteLockService.LockedUsers) invocation.callRealMethod();
				assigneeUsersLocked.countDown();
				await(releaseAssignee);
				return users;
			}
			ownerLockAttempted.countDown();
			return invocation.callRealMethod();
		}).when(lockTarget).lockUsers(any(User.class), any(Long[].class));

		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<TaskResponse> statusWrite = executor.submit(() -> taskService.updateStatus(
				authentication(assignee), task.getId(), TaskStatus.COMPLETED));
			assertThat(assigneeUsersLocked.await(5, TimeUnit.SECONDS)).isTrue();

			Future<TaskResponse> ownerUpdate = executor.submit(() -> taskService.update(
				authentication(owner),
				task.getId(),
				null,
				"Owner Update",
				"Updated while status changes",
				assignee.getId().toString(),
				TaskStatus.IN_PROGRESS,
				Priority.HIGH,
				LocalDate.of(2026, 7, 20)));
			assertThat(ownerLockAttempted.await(5, TimeUnit.SECONDS)).isTrue();
			assertThatThrownBy(() -> ownerUpdate.get(500, TimeUnit.MILLISECONDS))
				.isInstanceOf(TimeoutException.class);
			releaseAssignee.countDown();

			assertThat(statusWrite.get(10, TimeUnit.SECONDS).status())
				.isEqualTo(TaskStatus.COMPLETED);
			assertThat(ownerUpdate.get(10, TimeUnit.SECONDS).status())
				.isEqualTo(TaskStatus.IN_PROGRESS);
			assertThat(taskMapper.selectById(task.getId()).getStatus())
				.isEqualTo(TaskStatus.IN_PROGRESS);
		} finally {
			releaseAssignee.countDown();
			executor.shutdownNow();
			assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
		}
	}

	private User insertUser(String displayName) {
		return insertUser(displayName, SystemRole.USER);
	}

	private User insertUser(String displayName, SystemRole systemRole) {
		User user = new User();
		user.setUsername("task-lock-" + UUID.randomUUID());
		user.setPasswordHash("test-password-hash");
		user.setDisplayName(displayName);
		user.setSystemRole(systemRole);
		userMapper.insert(user);
		userIds.add(user.getId());
		return userMapper.selectById(user.getId());
	}

	private Project insertProject(User owner) {
		Project project = new Project();
		project.setOwnerId(owner.getId());
		project.setName("Task Write Lock " + UUID.randomUUID());
		project.setStatus(ProjectStatus.IN_PROGRESS);
		project.setPriority(Priority.MEDIUM);
		project.setStartDate(LocalDate.of(2026, 7, 1));
		project.setEndDate(LocalDate.of(2026, 7, 31));
		projectMapper.insert(project);
		projectId = project.getId();
		addMember(project, owner);
		return projectMapper.selectById(project.getId());
	}

	private void addMember(Project project, User user) {
		ProjectMember member = new ProjectMember();
		member.setProjectId(project.getId());
		member.setUserId(user.getId());
		projectMemberMapper.insert(member);
	}

	private Task insertTask(Project project, User creator, User assignee) {
		Task task = new Task();
		task.setProjectId(project.getId());
		task.setAssigneeId(assignee.getId());
		task.setCreatorId(creator.getId());
		task.setTitle("Concurrent Task");
		task.setStatus(TaskStatus.NOT_STARTED);
		task.setPriority(Priority.MEDIUM);
		task.setDueDate(LocalDate.of(2026, 7, 15));
		taskMapper.insert(task);
		return taskMapper.selectById(task.getId());
	}

	private static Authentication authentication(User user) {
		return UsernamePasswordAuthenticationToken.authenticated(
			user.getUsername(), "", List.of());
	}

	private static void await(CountDownLatch latch) {
		try {
			if (!latch.await(5, TimeUnit.SECONDS)) {
				throw new AssertionError("Timed out coordinating Task write locks");
			}
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new AssertionError(exception);
		}
	}

	private static void assertBusinessFailure(Future<?> future, ErrorCode expectedCode) {
		assertThatThrownBy(() -> future.get(10, TimeUnit.SECONDS))
			.isInstanceOf(ExecutionException.class)
			.hasCauseInstanceOf(BusinessException.class)
			.satisfies(exception -> assertThat(
				((BusinessException) exception.getCause()).code()).isEqualTo(expectedCode));
	}

	private static void shutdown(ExecutorService executor) throws InterruptedException {
		executor.shutdownNow();
		assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
	}
}
