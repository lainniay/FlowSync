package hgc.flowsync.task;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class TaskMapperTests {

	private final TaskMapper taskMapper;
	private final ProjectMapper projectMapper;
	private final ProjectMemberMapper projectMemberMapper;
	private final TaskLogMapper taskLogMapper;
	private final UserMapper userMapper;

	@Autowired
	TaskMapperTests(TaskMapper taskMapper, ProjectMapper projectMapper,
			ProjectMemberMapper projectMemberMapper, TaskLogMapper taskLogMapper, UserMapper userMapper) {
		this.taskMapper = taskMapper;
		this.projectMapper = projectMapper;
		this.projectMemberMapper = projectMemberMapper;
		this.taskLogMapper = taskLogMapper;
		this.userMapper = userMapper;
	}

	@Test
	void insertAndSelectMapTaskFields() {
		User creator = new User();
		creator.setUsername("task-owner-" + UUID.randomUUID());
		creator.setPasswordHash("test-password-hash");
		creator.setDisplayName("Task Creator");
		creator.setSystemRole(SystemRole.USER);
		assertThat(userMapper.insert(creator)).isOne();

		User assignee = new User();
		assignee.setUsername("task-assignee-" + UUID.randomUUID());
		assignee.setPasswordHash("test-password-hash");
		assignee.setDisplayName("Task Assignee");
		assignee.setSystemRole(SystemRole.USER);
		assertThat(userMapper.insert(assignee)).isOne();

		Project project = new Project();
		project.setOwnerId(creator.getId());
		project.setName("Task Mapper Project");
		project.setStatus(ProjectStatus.IN_PROGRESS);
		project.setPriority(Priority.HIGH);
		project.setStartDate(LocalDate.of(2026, 7, 1));
		project.setEndDate(LocalDate.of(2026, 7, 31));
		assertThat(projectMapper.insert(project)).isOne();

		ProjectMember ownerMember = new ProjectMember();
		ownerMember.setProjectId(project.getId());
		ownerMember.setUserId(creator.getId());
		assertThat(projectMemberMapper.insert(ownerMember)).isOne();

		ProjectMember assigneeMember = new ProjectMember();
		assigneeMember.setProjectId(project.getId());
		assigneeMember.setUserId(assignee.getId());
		assertThat(projectMemberMapper.insert(assigneeMember)).isOne();

		Task parent = new Task();
		parent.setProjectId(project.getId());
		parent.setCreatorId(creator.getId());
		parent.setAssigneeId(creator.getId());
		parent.setTitle("Parent Task");
		parent.setStatus(TaskStatus.COMPLETED);
		parent.setPriority(Priority.HIGH);
		assertThat(taskMapper.insert(parent)).isOne();

		Task task = new Task();
		task.setProjectId(project.getId());
		task.setParentId(parent.getId());
		task.setAssigneeId(assignee.getId());
		task.setCreatorId(creator.getId());
		task.setTitle("Mapper Task");
		task.setDescription("Task mapping test");
		task.setStatus(TaskStatus.BLOCKED);
		task.setPriority(Priority.MEDIUM);
		task.setDueDate(LocalDate.of(2026, 7, 20));

		assertThat(taskMapper.insert(task)).isOne();
		assertThat(task.getId()).isNotNull();

		Task saved = taskMapper.selectById(task.getId());
		assertThat(saved.getProjectId()).isEqualTo(project.getId());
		assertThat(saved.getParentId()).isEqualTo(parent.getId());
		assertThat(saved.getAssigneeId()).isEqualTo(assignee.getId());
		assertThat(saved.getCreatorId()).isEqualTo(creator.getId());
		assertThat(saved.getTitle()).isEqualTo("Mapper Task");
		assertThat(saved.getDescription()).isEqualTo("Task mapping test");
		assertThat(saved.getStatus()).isEqualTo(TaskStatus.BLOCKED);
		assertThat(saved.getPriority()).isEqualTo(Priority.MEDIUM);
		assertThat(saved.getDueDate()).isEqualTo(LocalDate.of(2026, 7, 20));
		assertThat(saved.getCreatedAt()).isNotNull();
		assertThat(saved.getUpdatedAt()).isNotNull();
		assertThat(taskMapper.existsIncompleteByAssigneeId(assignee.getId())).isTrue();
		assertThat(taskMapper.existsIncompleteByAssigneeId(creator.getId())).isFalse();
		assertThat(taskMapper.existsIncompleteByProjectIdAndAssigneeId(project.getId(), assignee.getId()))
			.isTrue();
		assertThat(taskMapper.existsIncompleteByProjectIdAndAssigneeId(project.getId(), creator.getId()))
			.isFalse();
		assertThat(taskMapper.existsIncompleteByProjectIdAndAssigneeId(Long.MAX_VALUE, assignee.getId()))
			.isFalse();
		assertThat(taskMapper.countByProjectId(project.getId())).isEqualTo(2);
		assertThat(taskMapper.countCompletedByProjectId(project.getId())).isOne();
		assertThat(taskMapper.countByProjectId(Long.MAX_VALUE)).isZero();
	}

	@Test
	void selectLatestProgressByTaskIdsReturnsOneLatestLogPerTask() {
		assertThat(taskMapper.selectLatestProgressByTaskIds(null)).isEmpty();
		assertThat(taskMapper.selectLatestProgressByTaskIds(List.of())).isEmpty();

		User user = new User();
		user.setUsername("task-progress-" + UUID.randomUUID());
		user.setPasswordHash("test-password-hash");
		user.setDisplayName("Task Progress User");
		user.setSystemRole(SystemRole.USER);
		assertThat(userMapper.insert(user)).isOne();

		Project project = new Project();
		project.setOwnerId(user.getId());
		project.setName("Task Progress Mapper Project");
		project.setStatus(ProjectStatus.IN_PROGRESS);
		project.setPriority(Priority.MEDIUM);
		assertThat(projectMapper.insert(project)).isOne();

		Task latestTask = insertProgressTask(project, user, "Latest Progress Task");
		Task tieTask = insertProgressTask(project, user, "Tie Progress Task");
		Task noLogTask = insertProgressTask(project, user, "No Log Task");

		LocalDateTime latestTime = LocalDateTime.of(2026, 7, 10, 12, 0);
		for (int index = 0; index < 20; index++) {
			insertProgressLog(latestTask, user, index, latestTime.minusDays(index + 1));
		}
		insertProgressLog(latestTask, user, 73, latestTime);

		LocalDateTime tieTime = LocalDateTime.of(2026, 7, 11, 12, 0);
		TaskLog firstTieLog = insertProgressLog(tieTask, user, 21, tieTime);
		TaskLog secondTieLog = insertProgressLog(tieTask, user, 84, tieTime);
		assertThat(secondTieLog.getId()).isGreaterThan(firstTieLog.getId());

		List<TaskMapper.LatestProgress> latestProgress = taskMapper.selectLatestProgressByTaskIds(
			List.of(latestTask.getId(), latestTask.getId(), tieTask.getId(), noLogTask.getId()));

		assertThat(latestProgress).hasSize(2);
		assertThat(latestProgress).extracting(TaskMapper.LatestProgress::getTaskId)
			.containsExactlyInAnyOrder(latestTask.getId(), tieTask.getId());
		assertThat(latestProgress).anySatisfy(progress -> {
			assertThat(progress.getTaskId()).isEqualTo(latestTask.getId());
			assertThat(progress.getProgressPercent()).isEqualTo(73);
		});
		assertThat(latestProgress).anySatisfy(progress -> {
			assertThat(progress.getTaskId()).isEqualTo(tieTask.getId());
			assertThat(progress.getProgressPercent()).isEqualTo(84);
		});
	}

	private Task insertProgressTask(Project project, User user, String title) {
		Task task = new Task();
		task.setProjectId(project.getId());
		task.setCreatorId(user.getId());
		task.setAssigneeId(user.getId());
		task.setTitle(title);
		task.setStatus(TaskStatus.IN_PROGRESS);
		task.setPriority(Priority.MEDIUM);
		assertThat(taskMapper.insert(task)).isOne();
		return task;
	}

	private TaskLog insertProgressLog(Task task, User user, int progressPercent, LocalDateTime createdAt) {
		TaskLog log = new TaskLog();
		log.setTaskId(task.getId());
		log.setOperatorId(user.getId());
		log.setProgressPercent(progressPercent);
		log.setContent("Progress " + progressPercent);
		assertThat(taskLogMapper.insert(log)).isOne();
		assertThat(taskLogMapper.update(null, Wrappers.<TaskLog>lambdaUpdate()
			.eq(TaskLog::getId, log.getId())
			.set(TaskLog::getCreatedAt, createdAt))).isOne();
		return log;
	}
}
