package hgc.flowsync.task;

import java.time.LocalDate;
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
	private final UserMapper userMapper;

	@Autowired
	TaskMapperTests(TaskMapper taskMapper, ProjectMapper projectMapper,
			ProjectMemberMapper projectMemberMapper, UserMapper userMapper) {
		this.taskMapper = taskMapper;
		this.projectMapper = projectMapper;
		this.projectMemberMapper = projectMemberMapper;
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
		parent.setTitle("Parent Task");
		parent.setStatus(TaskStatus.IN_PROGRESS);
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
	}
}
