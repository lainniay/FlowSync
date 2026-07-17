package hgc.flowsync.task;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import hgc.flowsync.common.error.BusinessException;
import hgc.flowsync.common.error.ErrorCode;
import hgc.flowsync.project.Priority;
import hgc.flowsync.project.Project;
import hgc.flowsync.project.ProjectAccessService;
import hgc.flowsync.project.ProjectMapper;
import hgc.flowsync.project.ProjectMember;
import hgc.flowsync.project.ProjectMemberMapper;
import hgc.flowsync.project.ProjectStatus;
import hgc.flowsync.user.CurrentUserService;
import hgc.flowsync.user.SystemRole;
import hgc.flowsync.user.User;
import hgc.flowsync.user.UserMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskAccessServiceTests {

	private static final long PROJECT_ID = 101L;
	private static final long TASK_ID = 501L;

	@Mock
	private TaskMapper taskMapper;
	@Mock
	private CurrentUserService currentUserService;
	@Mock
	private ProjectAccessService projectAccessService;
	@Mock
	private Authentication authentication;

	private TaskAccessService taskAccessService;
	private Project project;
	private Task task;
	private User owner;
	private User member;
	private User assignee;
	private User admin;
	private User outsider;

	@BeforeEach
	void setUp() {
		taskAccessService = new TaskAccessService(taskMapper, currentUserService, projectAccessService);
		owner = user(1L, SystemRole.USER);
		member = user(2L, SystemRole.USER);
		assignee = user(3L, SystemRole.USER);
		admin = user(4L, SystemRole.ADMIN);
		outsider = user(5L, SystemRole.USER);

		project = new Project();
		project.setId(PROJECT_ID);
		project.setOwnerId(owner.getId());
		task = new Task();
		task.setId(TASK_ID);
		task.setProjectId(PROJECT_ID);
		task.setAssigneeId(assignee.getId());
	}

	@Test
	void missingTaskReturnsNotFoundWithoutLoadingProject() {
		when(currentUserService.require(authentication)).thenReturn(member);

		assertBusinessError(
			() -> taskAccessService.requireReadable(authentication, TASK_ID),
			ErrorCode.NOT_FOUND);

		verify(projectAccessService, never()).requireProject(PROJECT_ID);
		verify(projectAccessService, never()).requireProjectForUpdate(PROJECT_ID);
	}

	@Test
	void readableTaskHidesFromOutsiderAndLoadsForMemberAndAdmin() {
		stubRead(member);
		when(projectAccessService.isMember(project, member)).thenReturn(false);
		assertBusinessError(
			() -> taskAccessService.requireReadable(authentication, TASK_ID),
			ErrorCode.NOT_FOUND);

		when(projectAccessService.isMember(project, member)).thenReturn(true);
		TaskAccessService.TaskContext memberContext =
			taskAccessService.requireReadable(authentication, TASK_ID);
		assertThat(memberContext.task()).isSameAs(task);
		assertThat(memberContext.project()).isSameAs(project);
		assertThat(memberContext.currentUser()).isSameAs(member);

		when(currentUserService.require(authentication)).thenReturn(admin);
		when(projectAccessService.isAdmin(admin)).thenReturn(true);
		TaskAccessService.TaskContext adminContext =
			taskAccessService.requireReadable(authentication, TASK_ID);
		assertThat(adminContext.task()).isSameAs(task);
		assertThat(adminContext.project()).isSameAs(project);
		assertThat(adminContext.currentUser()).isSameAs(admin);
		verify(projectAccessService, never()).requireProjectForUpdate(PROJECT_ID);
	}

	@Test
	void onlyOwnerCanUseCreateAndOwnerWriteAccess() {
		when(currentUserService.requireForUpdate(authentication)).thenReturn(owner);
		when(projectAccessService.requireProjectForUpdate(PROJECT_ID)).thenReturn(project);
		when(taskMapper.selectById(TASK_ID)).thenReturn(task);
		when(taskMapper.selectOne(any())).thenReturn(task);
		when(projectAccessService.isMember(project, owner)).thenReturn(true);

		TaskAccessService.ProjectContext createContext =
			taskAccessService.requireCreatable(authentication, PROJECT_ID);
		TaskAccessService.TaskContext writeContext =
			taskAccessService.requireOwnerWritable(authentication, TASK_ID);
		assertThat(createContext.project()).isSameAs(project);
		assertThat(createContext.currentUser()).isSameAs(owner);
		assertThat(writeContext.task()).isSameAs(task);
		verify(projectAccessService, times(2)).requireOwner(project, owner);

		clearInvocations(projectAccessService);
		when(currentUserService.requireForUpdate(authentication)).thenReturn(member);
		when(projectAccessService.isMember(project, member)).thenReturn(true);
		doThrow(new BusinessException(ErrorCode.FORBIDDEN))
			.when(projectAccessService).requireOwner(project, member);
		assertBusinessError(
			() -> taskAccessService.requireCreatable(authentication, PROJECT_ID),
			ErrorCode.FORBIDDEN);
		assertBusinessError(
			() -> taskAccessService.requireOwnerWritable(authentication, TASK_ID),
			ErrorCode.FORBIDDEN);
		verify(projectAccessService, never()).requireUnarchived(project);
	}

	@Test
	void onlyOwnerOrAssigneeCanChangeStatusAndCreateTaskLog() {
		stubWrite(owner);
		when(projectAccessService.isMember(project, owner)).thenReturn(true);
		when(projectAccessService.isOwner(project, owner)).thenReturn(true);
		assertThatCode(() -> taskAccessService.requireStatusWritable(authentication, TASK_ID))
			.doesNotThrowAnyException();
		assertThatCode(() -> taskAccessService.requireTaskLogCreatable(authentication, TASK_ID))
			.doesNotThrowAnyException();

		stubWrite(assignee);
		when(projectAccessService.isOwner(project, assignee)).thenReturn(false);
		when(projectAccessService.isMember(project, assignee)).thenReturn(true);
		assertThatCode(() -> taskAccessService.requireStatusWritable(authentication, TASK_ID))
			.doesNotThrowAnyException();
		assertThatCode(() -> taskAccessService.requireTaskLogCreatable(authentication, TASK_ID))
			.doesNotThrowAnyException();

		clearInvocations(projectAccessService);
		stubWrite(member);
		when(projectAccessService.isMember(project, member)).thenReturn(true);
		when(projectAccessService.isOwner(project, member)).thenReturn(false);
		assertBusinessError(
			() -> taskAccessService.requireStatusWritable(authentication, TASK_ID),
			ErrorCode.FORBIDDEN);
		assertBusinessError(
			() -> taskAccessService.requireTaskLogCreatable(authentication, TASK_ID),
			ErrorCode.FORBIDDEN);
		verify(projectAccessService, never()).requireUnarchived(project);
	}

	@Test
	void removedAssigneeIsHiddenFromStatusAndTaskLogWrites() {
		stubWrite(assignee);
		when(projectAccessService.isMember(project, assignee)).thenReturn(false);

		assertBusinessError(
			() -> taskAccessService.requireStatusWritable(authentication, TASK_ID),
			ErrorCode.NOT_FOUND);
		assertBusinessError(
			() -> taskAccessService.requireTaskLogCreatable(authentication, TASK_ID),
			ErrorCode.NOT_FOUND);
		verify(projectAccessService, never()).requireUnarchived(project);
	}

	@Test
	void outsiderIsHiddenFromEveryExistingTaskWrite() {
		stubWrite(outsider);
		when(projectAccessService.isMember(project, outsider)).thenReturn(false);

		assertBusinessError(
			() -> taskAccessService.requireOwnerWritable(authentication, TASK_ID),
			ErrorCode.NOT_FOUND);
		assertBusinessError(
			() -> taskAccessService.requireStatusWritable(authentication, TASK_ID),
			ErrorCode.NOT_FOUND);
		assertBusinessError(
			() -> taskAccessService.requireTaskLogCreatable(authentication, TASK_ID),
			ErrorCode.NOT_FOUND);
		verify(projectAccessService, never()).requireOwner(project, outsider);
		verify(projectAccessService, never()).requireUnarchived(project);
	}

	@Test
	void adminCannotUseAnyWriteAccessAndFailureStopsArchiveCheck() {
		when(currentUserService.requireForUpdate(authentication)).thenReturn(admin);
		when(projectAccessService.requireProjectForUpdate(PROJECT_ID)).thenReturn(project);
		when(taskMapper.selectById(TASK_ID)).thenReturn(task);
		when(taskMapper.selectOne(any())).thenReturn(task);
		when(projectAccessService.isAdmin(admin)).thenReturn(true);

		assertBusinessError(
			() -> taskAccessService.requireCreatable(authentication, PROJECT_ID),
			ErrorCode.FORBIDDEN);
		assertBusinessError(
			() -> taskAccessService.requireOwnerWritable(authentication, TASK_ID),
			ErrorCode.FORBIDDEN);
		assertBusinessError(
			() -> taskAccessService.requireStatusWritable(authentication, TASK_ID),
			ErrorCode.FORBIDDEN);
		assertBusinessError(
			() -> taskAccessService.requireTaskLogCreatable(authentication, TASK_ID),
			ErrorCode.FORBIDDEN);
		verify(projectAccessService, never()).requireUnarchived(project);
	}

	@Test
	void archivedProjectBlocksEveryAuthorizedWriteAccess() {
		project.setArchivedAt(LocalDateTime.of(2026, 7, 17, 12, 0));
		stubWrite(owner);
		when(projectAccessService.isMember(project, owner)).thenReturn(true);
		when(projectAccessService.isOwner(project, owner)).thenReturn(true);
		doThrow(new BusinessException(ErrorCode.PROJECT_ARCHIVED))
			.when(projectAccessService).requireUnarchived(project);

		assertBusinessError(
			() -> taskAccessService.requireCreatable(authentication, PROJECT_ID),
			ErrorCode.PROJECT_ARCHIVED);
		assertBusinessError(
			() -> taskAccessService.requireOwnerWritable(authentication, TASK_ID),
			ErrorCode.PROJECT_ARCHIVED);
		assertBusinessError(
			() -> taskAccessService.requireStatusWritable(authentication, TASK_ID),
			ErrorCode.PROJECT_ARCHIVED);
		assertBusinessError(
			() -> taskAccessService.requireTaskLogCreatable(authentication, TASK_ID),
			ErrorCode.PROJECT_ARCHIVED);
	}

	@Test
	void writeAccessLocksUserThenProjectAndReloadsTaskForUpdate() {
		stubWrite(owner);
		when(projectAccessService.isMember(project, owner)).thenReturn(true);

		taskAccessService.requireOwnerWritable(authentication, TASK_ID);

		var order = inOrder(currentUserService, taskMapper, projectAccessService);
		order.verify(currentUserService).requireForUpdate(authentication);
		order.verify(taskMapper).selectById(TASK_ID);
		order.verify(projectAccessService).requireProjectForUpdate(PROJECT_ID);
		order.verify(taskMapper).selectOne(any());
	}

	@Test
	void taskRemovedBeforeWriteLockReturnsNotFound() {
		when(currentUserService.requireForUpdate(authentication)).thenReturn(owner);
		when(taskMapper.selectById(TASK_ID)).thenReturn(task);
		when(projectAccessService.requireProjectForUpdate(PROJECT_ID)).thenReturn(project);

		assertBusinessError(
			() -> taskAccessService.requireOwnerWritable(authentication, TASK_ID),
			ErrorCode.NOT_FOUND);
		verify(projectAccessService, never()).requireOwner(project, owner);
		verify(projectAccessService, never()).requireUnarchived(project);
	}

	@Test
	void statusAuthorizationUsesLatestAssigneeAfterProjectLock() {
		Task latestTask = task(TASK_ID, PROJECT_ID, assignee.getId());
		task.setAssigneeId(member.getId());
		when(currentUserService.requireForUpdate(authentication)).thenReturn(assignee);
		when(taskMapper.selectById(TASK_ID)).thenReturn(task);
		when(projectAccessService.requireProjectForUpdate(PROJECT_ID)).thenReturn(project);
		when(taskMapper.selectOne(any())).thenReturn(latestTask);
		when(projectAccessService.isMember(project, assignee)).thenReturn(true);

		TaskAccessService.TaskContext context =
			taskAccessService.requireStatusWritable(authentication, TASK_ID);

		assertThat(context.task()).isSameAs(latestTask);
	}

	@Test
	void taskMovedAfterProjectLockReturnsNotFound() {
		Task movedTask = task(TASK_ID, PROJECT_ID + 1, owner.getId());
		when(currentUserService.requireForUpdate(authentication)).thenReturn(owner);
		when(taskMapper.selectById(TASK_ID)).thenReturn(task);
		when(projectAccessService.requireProjectForUpdate(PROJECT_ID)).thenReturn(project);
		when(taskMapper.selectOne(any())).thenReturn(movedTask);

		assertBusinessError(
			() -> taskAccessService.requireOwnerWritable(authentication, TASK_ID),
			ErrorCode.NOT_FOUND);
		verify(projectAccessService, never()).requireOwner(project, owner);
		verify(projectAccessService, never()).requireUnarchived(project);
	}

	private void stubRead(User currentUser) {
		when(currentUserService.require(authentication)).thenReturn(currentUser);
		when(taskMapper.selectById(TASK_ID)).thenReturn(task);
		when(projectAccessService.requireProject(PROJECT_ID)).thenReturn(project);
	}

	private void stubWrite(User currentUser) {
		when(currentUserService.requireForUpdate(authentication)).thenReturn(currentUser);
		when(taskMapper.selectById(TASK_ID)).thenReturn(task);
		when(projectAccessService.requireProjectForUpdate(PROJECT_ID)).thenReturn(project);
		when(taskMapper.selectOne(any())).thenReturn(task);
	}

	private static User user(Long id, SystemRole role) {
		User user = new User();
		user.setId(id);
		user.setSystemRole(role);
		return user;
	}

	private static Task task(Long id, Long projectId, Long assigneeId) {
		Task task = new Task();
		task.setId(id);
		task.setProjectId(projectId);
		task.setAssigneeId(assigneeId);
		return task;
	}

	private static void assertBusinessError(
		org.assertj.core.api.ThrowableAssert.ThrowingCallable action,
		ErrorCode expected) {
		assertThatThrownBy(action)
			.isInstanceOfSatisfying(BusinessException.class,
				exception -> assertThat(exception.code()).isEqualTo(expected));
	}

	@Nested
	@SpringBootTest
	class LockingTests {

	private final TaskAccessService taskAccessService;
	private final TaskMapper taskMapper;
	private final ProjectMapper projectMapper;
	private final ProjectMemberMapper projectMemberMapper;
	private final UserMapper userMapper;
	private final TransactionTemplate transactionTemplate;

	private Long taskId;
	private Long projectId;
	private Long ownerId;
	private String ownerUsername;

	@Autowired
	LockingTests(
		TaskAccessService taskAccessService,
		TaskMapper taskMapper,
		ProjectMapper projectMapper,
		ProjectMemberMapper projectMemberMapper,
		UserMapper userMapper,
		PlatformTransactionManager transactionManager) {
		this.taskAccessService = taskAccessService;
		this.taskMapper = taskMapper;
		this.projectMapper = projectMapper;
		this.projectMemberMapper = projectMemberMapper;
		this.userMapper = userMapper;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
	}

	@BeforeEach
	void createTask() {
		transactionTemplate.executeWithoutResult(status -> {
			User owner = new User();
			ownerUsername = "task-lock-" + UUID.randomUUID().toString().substring(0, 20);
			owner.setUsername(ownerUsername);
			owner.setPasswordHash("test-password-hash");
			owner.setDisplayName("Task Lock Owner");
			owner.setSystemRole(SystemRole.USER);
			userMapper.insert(owner);
			ownerId = owner.getId();

			Project project = new Project();
			project.setOwnerId(ownerId);
			project.setName("Task Lock Project");
			project.setStatus(ProjectStatus.IN_PROGRESS);
			project.setPriority(Priority.MEDIUM);
			project.setStartDate(LocalDate.of(2026, 7, 1));
			project.setEndDate(LocalDate.of(2026, 7, 31));
			projectMapper.insert(project);
			projectId = project.getId();

			ProjectMember member = new ProjectMember();
			member.setProjectId(projectId);
			member.setUserId(ownerId);
			projectMemberMapper.insert(member);

			Task task = new Task();
			task.setProjectId(projectId);
			task.setAssigneeId(ownerId);
			task.setCreatorId(ownerId);
			task.setTitle("Locked Task");
			task.setStatus(TaskStatus.NOT_STARTED);
			task.setPriority(Priority.MEDIUM);
			taskMapper.insert(task);
			taskId = task.getId();
		});
	}

	@AfterEach
	void removeTask() {
		if (projectId == null || ownerId == null) {
			return;
		}
		transactionTemplate.executeWithoutResult(status -> {
			taskMapper.delete(Wrappers.<Task>lambdaQuery().eq(Task::getProjectId, projectId));
			projectMemberMapper.delete(Wrappers.<ProjectMember>lambdaQuery()
				.eq(ProjectMember::getProjectId, projectId));
			projectMapper.deleteById(projectId);
			userMapper.deleteById(ownerId);
		});
	}

	@Test
	void writableContextHoldsTaskLockUntilTransactionCompletes() throws Exception {
		CountDownLatch accessGranted = new CountDownLatch(1);
		CountDownLatch releaseAccess = new CountDownLatch(1);
		CountDownLatch deletionStarted = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<?> holder = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
				taskAccessService.requireOwnerWritable(authentication(), taskId);
				accessGranted.countDown();
				await(releaseAccess);
			}));
			assertThat(accessGranted.await(5, TimeUnit.SECONDS)).isTrue();

			Future<Integer> deletion = executor.submit(() -> transactionTemplate.execute(status -> {
				deletionStarted.countDown();
				return taskMapper.deleteById(taskId);
			}));
			assertThat(deletionStarted.await(5, TimeUnit.SECONDS)).isTrue();
			assertThatThrownBy(() -> deletion.get(500, TimeUnit.MILLISECONDS))
				.isInstanceOf(TimeoutException.class);

			releaseAccess.countDown();
			holder.get(5, TimeUnit.SECONDS);
			assertThat(deletion.get(5, TimeUnit.SECONDS)).isOne();
		} finally {
			releaseAccess.countDown();
			executor.shutdownNow();
			executor.awaitTermination(5, TimeUnit.SECONDS);
		}
	}

	@Test
	void everyWriteAccessMethodRequiresAnOuterTransaction() {
		assertThatThrownBy(() -> taskAccessService.requireCreatable(authentication(), projectId))
			.isInstanceOf(IllegalTransactionStateException.class);
		assertThatThrownBy(() -> taskAccessService.requireOwnerWritable(authentication(), taskId))
			.isInstanceOf(IllegalTransactionStateException.class);
		assertThatThrownBy(() -> taskAccessService.requireStatusWritable(authentication(), taskId))
			.isInstanceOf(IllegalTransactionStateException.class);
		assertThatThrownBy(() -> taskAccessService.requireTaskLogCreatable(authentication(), taskId))
			.isInstanceOf(IllegalTransactionStateException.class);
	}

	@Test
	void everyWriteAccessMethodWorksInsideAnOuterTransaction() {
		transactionTemplate.executeWithoutResult(status -> {
			assertThatCode(() -> taskAccessService.requireCreatable(authentication(), projectId))
				.doesNotThrowAnyException();
			assertThatCode(() -> taskAccessService.requireOwnerWritable(authentication(), taskId))
				.doesNotThrowAnyException();
			assertThatCode(() -> taskAccessService.requireStatusWritable(authentication(), taskId))
				.doesNotThrowAnyException();
			assertThatCode(() -> taskAccessService.requireTaskLogCreatable(authentication(), taskId))
				.doesNotThrowAnyException();
		});
	}

	@Test
	void readableAccessDoesNotRequireATransaction() {
		TaskAccessService.TaskContext context =
			taskAccessService.requireReadable(authentication(), taskId);

		assertThat(context.task().getId()).isEqualTo(taskId);
		assertThat(context.project().getId()).isEqualTo(projectId);
	}

	private Authentication authentication() {
		return UsernamePasswordAuthenticationToken.authenticated(ownerUsername, "", List.of());
	}

	private static void await(CountDownLatch latch) {
		try {
			if (!latch.await(5, TimeUnit.SECONDS)) {
				throw new AssertionError("Timed out waiting to release task access");
			}
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new AssertionError(exception);
		}
	}
}
}
