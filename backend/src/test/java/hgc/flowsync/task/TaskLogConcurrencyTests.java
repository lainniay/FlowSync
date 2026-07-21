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

import hgc.flowsync.common.error.BusinessException;
import hgc.flowsync.common.error.ErrorCode;
import hgc.flowsync.project.Priority;
import hgc.flowsync.project.Project;
import hgc.flowsync.project.ProjectMapper;
import hgc.flowsync.project.ProjectMember;
import hgc.flowsync.project.ProjectMemberMapper;
import hgc.flowsync.project.ProjectService;
import hgc.flowsync.project.ProjectStatus;
import hgc.flowsync.user.SystemRole;
import hgc.flowsync.user.User;
import hgc.flowsync.user.UserMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class TaskLogConcurrencyTests {

	@Autowired
	private TaskLogService taskLogService;
	@Autowired
	private TaskService taskService;
	@Autowired
	private ProjectService projectService;
	@Autowired
	private TaskLogMapper taskLogMapper;
	@Autowired
	private TaskMapper taskMapper;
	@Autowired
	private ProjectMapper projectMapper;
	@Autowired
	private ProjectMemberMapper projectMemberMapper;
	@Autowired
	private UserMapper userMapper;
	@Autowired
	private PlatformTransactionManager transactionManager;

	private final List<Long> userIds = new ArrayList<>();
	private final List<Long> projectIds = new ArrayList<>();

	@AfterEach
	void deleteCreatedRecords() {
		for (Long projectId : projectIds) {
			List<Long> taskIds = taskMapper.selectList(Wrappers.<Task>lambdaQuery()
				.select(Task::getId)
				.eq(Task::getProjectId, projectId)).stream()
				.map(Task::getId)
				.toList();
			if (!taskIds.isEmpty()) {
				taskLogMapper.delete(Wrappers.<TaskLog>lambdaQuery()
					.in(TaskLog::getTaskId, taskIds));
				taskMapper.deleteBatchIds(taskIds);
			}
			projectMemberMapper.delete(Wrappers.<ProjectMember>lambdaQuery()
				.eq(ProjectMember::getProjectId, projectId));
			projectMapper.deleteById(projectId);
		}
		if (!userIds.isEmpty()) {
			userMapper.deleteBatchIds(userIds);
		}
	}

	@Test
	void archiveCommittedBeforeCreateAuthorizationPreventsInsert() throws Exception {
		User owner = insertUser("Owner");
		User assignee = insertUser("Assignee");
		Project project = insertProject(owner);
		addMember(project, assignee);
		Task task = insertTask(project, owner, assignee);
		CountDownLatch archiveApplied = new CountDownLatch(1);
		CountDownLatch releaseArchive = new CountDownLatch(1);
		CountDownLatch createAttempted = new CountDownLatch(1);

		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<?> archive = executor.submit(() -> inTransaction(() -> {
				projectService.archive(authentication(owner), project.getId());
				archiveApplied.countDown();
				await(releaseArchive);
			}));
			assertThat(archiveApplied.await(5, TimeUnit.SECONDS)).isTrue();

			Future<TaskLogResponse> create = executor.submit(() -> {
				createAttempted.countDown();
				return taskLogService.create(
					authentication(assignee), task.getId(), 25, "Blocked by archive");
			});
			assertThat(createAttempted.await(5, TimeUnit.SECONDS)).isTrue();
			assertThatThrownBy(() -> create.get(500, TimeUnit.MILLISECONDS))
				.isInstanceOf(TimeoutException.class);

			releaseArchive.countDown();
			archive.get(10, TimeUnit.SECONDS);
			assertBusinessFailure(create, ErrorCode.PROJECT_ARCHIVED);
			assertThat(taskLogMapper.selectCount(Wrappers.<TaskLog>lambdaQuery()
				.eq(TaskLog::getTaskId, task.getId()))).isZero();
		} finally {
			releaseArchive.countDown();
			shutdown(executor);
		}
	}

	@Test
	void archiveCommittedBeforeDeleteAuthorizationPreservesLog() throws Exception {
		User owner = insertUser("Owner");
		User operator = insertUser("Operator");
		Project project = insertProject(owner);
		addMember(project, operator);
		Task task = insertTask(project, owner, operator);
		TaskLog taskLog = insertLog(task, operator, 50);
		CountDownLatch archiveApplied = new CountDownLatch(1);
		CountDownLatch releaseArchive = new CountDownLatch(1);
		CountDownLatch deleteAttempted = new CountDownLatch(1);

		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<?> archive = executor.submit(() -> inTransaction(() -> {
				projectService.archive(authentication(owner), project.getId());
				archiveApplied.countDown();
				await(releaseArchive);
			}));
			assertThat(archiveApplied.await(5, TimeUnit.SECONDS)).isTrue();

			Future<?> delete = executor.submit(() -> {
				deleteAttempted.countDown();
				taskLogService.delete(authentication(operator), task.getId(), taskLog.getId());
			});
			assertThat(deleteAttempted.await(5, TimeUnit.SECONDS)).isTrue();
			assertThatThrownBy(() -> delete.get(500, TimeUnit.MILLISECONDS))
				.isInstanceOf(TimeoutException.class);

			releaseArchive.countDown();
			archive.get(10, TimeUnit.SECONDS);
			assertBusinessFailure(delete, ErrorCode.PROJECT_ARCHIVED);
			assertThat(taskLogMapper.selectById(taskLog.getId())).isNotNull();
		} finally {
			releaseArchive.countDown();
			shutdown(executor);
		}
	}

	@Test
	void concurrentDeletesSerializeAndSecondDeleteReturnsNotFound() throws Exception {
		User owner = insertUser("Owner");
		User operator = insertUser("Operator");
		Project project = insertProject(owner);
		addMember(project, operator);
		Task task = insertTask(project, owner, operator);
		TaskLog taskLog = insertLog(task, operator, 60);
		CountDownLatch firstDeleteApplied = new CountDownLatch(1);
		CountDownLatch releaseFirstDelete = new CountDownLatch(1);
		CountDownLatch secondDeleteAttempted = new CountDownLatch(1);

		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<?> firstDelete = executor.submit(() -> inTransaction(() -> {
				taskLogService.delete(authentication(operator), task.getId(), taskLog.getId());
				firstDeleteApplied.countDown();
				await(releaseFirstDelete);
			}));
			assertThat(firstDeleteApplied.await(5, TimeUnit.SECONDS)).isTrue();

			Future<?> secondDelete = executor.submit(() -> {
				secondDeleteAttempted.countDown();
				taskLogService.delete(authentication(owner), task.getId(), taskLog.getId());
			});
			assertThat(secondDeleteAttempted.await(5, TimeUnit.SECONDS)).isTrue();
			assertThatThrownBy(() -> secondDelete.get(500, TimeUnit.MILLISECONDS))
				.isInstanceOf(TimeoutException.class);

			releaseFirstDelete.countDown();
			firstDelete.get(10, TimeUnit.SECONDS);
			assertBusinessFailure(secondDelete, ErrorCode.NOT_FOUND);
			assertThat(taskLogMapper.selectById(taskLog.getId())).isNull();
		} finally {
			releaseFirstDelete.countDown();
			shutdown(executor);
		}
	}

	@Test
	void createAuthorizationUsesAssigneeReloadedAfterProjectAndTaskLocks() throws Exception {
		User owner = insertUser("Owner");
		User formerAssignee = insertUser("Former Assignee");
		User replacement = insertUser("Replacement");
		Project project = insertProject(owner);
		addMember(project, formerAssignee);
		addMember(project, replacement);
		Task task = insertTask(project, owner, formerAssignee);
		CountDownLatch updateApplied = new CountDownLatch(1);
		CountDownLatch releaseUpdate = new CountDownLatch(1);
		CountDownLatch createAttempted = new CountDownLatch(1);

		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<?> update = executor.submit(() -> inTransaction(() -> {
				taskService.update(
					authentication(owner),
					task.getId(),
					null,
					"Reassigned Task",
					"Assignee changed before log creation",
					replacement.getId().toString(),
					TaskStatus.IN_PROGRESS,
					Priority.HIGH,
					LocalDate.of(2026, 7, 20));
				updateApplied.countDown();
				await(releaseUpdate);
			}));
			assertThat(updateApplied.await(5, TimeUnit.SECONDS)).isTrue();

			Future<TaskLogResponse> create = executor.submit(() -> {
				createAttempted.countDown();
				return taskLogService.create(
					authentication(formerAssignee), task.getId(), 30, "Stale assignee");
			});
			assertThat(createAttempted.await(5, TimeUnit.SECONDS)).isTrue();
			assertThatThrownBy(() -> create.get(500, TimeUnit.MILLISECONDS))
				.isInstanceOf(TimeoutException.class);

			releaseUpdate.countDown();
			update.get(10, TimeUnit.SECONDS);
			assertBusinessFailure(create, ErrorCode.FORBIDDEN);
			assertThat(taskMapper.selectById(task.getId()).getAssigneeId())
				.isEqualTo(replacement.getId());
			assertThat(taskLogMapper.selectCount(Wrappers.<TaskLog>lambdaQuery()
				.eq(TaskLog::getTaskId, task.getId()))).isZero();
		} finally {
			releaseUpdate.countDown();
			shutdown(executor);
		}
	}

	private void inTransaction(Runnable action) {
		new TransactionTemplate(transactionManager).executeWithoutResult(status -> action.run());
	}

	private User insertUser(String displayName) {
		User user = new User();
		user.setUsername("task-log-lock-" + UUID.randomUUID());
		user.setPasswordHash("test-password-hash");
		user.setDisplayName(displayName);
		user.setSystemRole(SystemRole.USER);
		user.setActive(true);
		userMapper.insert(user);
		userIds.add(user.getId());
		return userMapper.selectById(user.getId());
	}

	private Project insertProject(User owner) {
		Project project = new Project();
		project.setOwnerId(owner.getId());
		project.setName("Task Log Lock " + UUID.randomUUID());
		project.setStatus(ProjectStatus.IN_PROGRESS);
		project.setPriority(Priority.MEDIUM);
		project.setStartDate(LocalDate.of(2026, 7, 1));
		project.setEndDate(LocalDate.of(2026, 7, 31));
		projectMapper.insert(project);
		projectIds.add(project.getId());
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
		task.setTitle("Concurrent Task Log");
		task.setStatus(TaskStatus.NOT_STARTED);
		task.setPriority(Priority.MEDIUM);
		task.setDueDate(LocalDate.of(2026, 7, 15));
		taskMapper.insert(task);
		return taskMapper.selectById(task.getId());
	}

	private TaskLog insertLog(Task task, User operator, int progressPercent) {
		TaskLog taskLog = new TaskLog();
		taskLog.setTaskId(task.getId());
		taskLog.setOperatorId(operator.getId());
		taskLog.setProgressPercent(progressPercent);
		taskLog.setContent("Concurrent task log");
		taskLogMapper.insert(taskLog);
		return taskLogMapper.selectById(taskLog.getId());
	}

	private static Authentication authentication(User user) {
		return UsernamePasswordAuthenticationToken.authenticated(
			user.getUsername(), "", List.of());
	}

	private static void assertBusinessFailure(Future<?> future, ErrorCode expectedCode) {
		assertThatThrownBy(() -> future.get(10, TimeUnit.SECONDS))
			.isInstanceOf(ExecutionException.class)
			.hasCauseInstanceOf(BusinessException.class)
			.satisfies(exception -> assertThat(
				((BusinessException) exception.getCause()).code()).isEqualTo(expectedCode));
	}

	private static void await(CountDownLatch latch) {
		try {
			if (!latch.await(5, TimeUnit.SECONDS)) {
				throw new AssertionError("Timed out coordinating TaskLog write locks");
			}
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new AssertionError(exception);
		}
	}

	private static void shutdown(ExecutorService executor) throws InterruptedException {
		executor.shutdownNow();
		assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
	}
}
