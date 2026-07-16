package hgc.flowsync.summary;

import java.util.UUID;

import hgc.flowsync.project.Priority;
import hgc.flowsync.project.Project;
import hgc.flowsync.project.ProjectMapper;
import hgc.flowsync.project.ProjectMember;
import hgc.flowsync.project.ProjectMemberMapper;
import hgc.flowsync.project.ProjectStatus;
import hgc.flowsync.task.Task;
import hgc.flowsync.task.TaskLog;
import hgc.flowsync.task.TaskLogMapper;
import hgc.flowsync.task.TaskMapper;
import hgc.flowsync.task.TaskStatus;
import hgc.flowsync.user.SystemRole;
import hgc.flowsync.user.User;
import hgc.flowsync.user.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class TaskLogSummaryMapperTests {

	@Autowired
	private UserMapper userMapper;
	@Autowired
	private ProjectMapper projectMapper;
	@Autowired
	private ProjectMemberMapper projectMemberMapper;
	@Autowired
	private TaskMapper taskMapper;
	@Autowired
	private TaskLogMapper taskLogMapper;
	@Autowired
	private SummaryMapper summaryMapper;

	@Test
	void insertAndSelectMapTaskLogAndSummaryFields() {
		User user = new User();
		user.setUsername("history-" + UUID.randomUUID());
		user.setPasswordHash("test-password-hash");
		user.setDisplayName("History Author");
		user.setSystemRole(SystemRole.USER);
		assertThat(userMapper.insert(user)).isOne();

		Project project = new Project();
		project.setOwnerId(user.getId());
		project.setName("History Mapper Project");
		project.setStatus(ProjectStatus.IN_PROGRESS);
		project.setPriority(Priority.HIGH);
		assertThat(projectMapper.insert(project)).isOne();

		ProjectMember member = new ProjectMember();
		member.setProjectId(project.getId());
		member.setUserId(user.getId());
		assertThat(projectMemberMapper.insert(member)).isOne();

		Task task = new Task();
		task.setProjectId(project.getId());
		task.setAssigneeId(user.getId());
		task.setCreatorId(user.getId());
		task.setTitle("History Mapper Task");
		task.setStatus(TaskStatus.IN_PROGRESS);
		task.setPriority(Priority.HIGH);
		assertThat(taskMapper.insert(task)).isOne();

		TaskLog taskLog = new TaskLog();
		taskLog.setTaskId(task.getId());
		taskLog.setOperatorId(user.getId());
		taskLog.setProgressPercent(40);
		taskLog.setContent("Task progress mapping test");
		assertThat(taskLogMapper.insert(taskLog)).isOne();

		TaskLog savedLog = taskLogMapper.selectById(taskLog.getId());
		assertThat(savedLog.getTaskId()).isEqualTo(task.getId());
		assertThat(savedLog.getOperatorId()).isEqualTo(user.getId());
		assertThat(savedLog.getProgressPercent()).isEqualTo(40);
		assertThat(savedLog.getContent()).isEqualTo("Task progress mapping test");
		assertThat(savedLog.getCreatedAt()).isNotNull();

		Summary summary = new Summary();
		summary.setProjectId(project.getId());
		summary.setTaskId(task.getId());
		summary.setCreatedBy(user.getId());
		summary.setType(SummaryType.STAGE);
		summary.setContent("Stage summary mapping test");
		assertThat(summaryMapper.insert(summary)).isOne();

		Summary savedSummary = summaryMapper.selectById(summary.getId());
		assertThat(savedSummary.getProjectId()).isEqualTo(project.getId());
		assertThat(savedSummary.getTaskId()).isEqualTo(task.getId());
		assertThat(savedSummary.getCreatedBy()).isEqualTo(user.getId());
		assertThat(savedSummary.getType()).isEqualTo(SummaryType.STAGE);
		assertThat(savedSummary.getContent()).isEqualTo("Stage summary mapping test");
		assertThat(savedSummary.getCreatedAt()).isNotNull();
		assertThat(savedSummary.getUpdatedAt()).isNotNull();
	}
}
