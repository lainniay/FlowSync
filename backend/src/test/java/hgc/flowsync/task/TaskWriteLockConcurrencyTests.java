package hgc.flowsync.task;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import hgc.flowsync.project.Priority;
import hgc.flowsync.project.Project;
import hgc.flowsync.project.ProjectMapper;
import hgc.flowsync.project.ProjectMember;
import hgc.flowsync.project.ProjectMemberMapper;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest
class TaskWriteLockConcurrencyTests {

	@Autowired
	private TaskService taskService;
	@Autowired
	private TaskMapper taskMapper;
	@Autowired
	private ProjectMapper projectMapper;
	@Autowired
	private ProjectMemberMapper projectMemberMapper;
	@Autowired
	private UserMapper userMapper;
	@MockitoSpyBean
	private TaskWriteLockService taskWriteLockService;

	private final List<Long> userIds = new ArrayList<>();
	private Long projectId;

	@AfterEach
	void deleteCreatedRecords() {
		if (projectId != null) {
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
	void ownerFullUpdateAndAssigneeStatusWriteCompleteWithoutDeadlock() throws Exception {
		User owner = insertUser("Owner");
		User assignee = insertUser("Assignee");
		Project project = insertProject(owner);
		addMember(project, assignee);
		Task task = insertTask(project, owner, assignee);
		CountDownLatch assigneeUsersLocked = new CountDownLatch(1);
		CountDownLatch ownerLockAttempted = new CountDownLatch(1);
		CountDownLatch releaseAssignee = new CountDownLatch(1);

		TaskWriteLockService lockTarget = AopTestUtils.getUltimateTargetObject(taskWriteLockService);
		doAnswer(invocation -> {
			User currentUser = invocation.getArgument(0, User.class);
			if (currentUser.getId().equals(assignee.getId())) {
				TaskWriteLockService.LockedUsers users =
					(TaskWriteLockService.LockedUsers) invocation.callRealMethod();
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
		User user = new User();
		user.setUsername("task-lock-" + UUID.randomUUID());
		user.setPasswordHash("test-password-hash");
		user.setDisplayName(displayName);
		user.setSystemRole(SystemRole.USER);
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
}
