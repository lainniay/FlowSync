package hgc.flowsync.devseed;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import hgc.flowsync.project.InvitationStatus;
import hgc.flowsync.project.Priority;
import hgc.flowsync.project.Project;
import hgc.flowsync.project.ProjectInvitation;
import hgc.flowsync.project.ProjectInvitationMapper;
import hgc.flowsync.project.ProjectMapper;
import hgc.flowsync.project.ProjectMember;
import hgc.flowsync.project.ProjectMemberMapper;
import hgc.flowsync.project.ProjectStatus;
import hgc.flowsync.summary.Summary;
import hgc.flowsync.summary.SummaryMapper;
import hgc.flowsync.task.Task;
import hgc.flowsync.task.TaskLog;
import hgc.flowsync.task.TaskLogMapper;
import hgc.flowsync.task.TaskMapper;
import hgc.flowsync.task.TaskStatus;
import hgc.flowsync.user.SystemRole;
import hgc.flowsync.user.User;
import hgc.flowsync.user.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class DevDataSeederTests {

	private static final String MARKER = "[DEVSEED-TEST]";
	private static final String USER_PREFIX = "devseed-test-user-";

	@Autowired
	private UserMapper userMapper;
	@Autowired
	private ProjectMapper projectMapper;
	@Autowired
	private ProjectMemberMapper projectMemberMapper;
	@Autowired
	private ProjectInvitationMapper projectInvitationMapper;
	@Autowired
	private TaskMapper taskMapper;
	@Autowired
	private TaskLogMapper taskLogMapper;
	@Autowired
	private SummaryMapper summaryMapper;
	@Autowired
	private PasswordEncoder passwordEncoder;
	@Autowired
	private PlatformTransactionManager transactionManager;

	private DevDataSeeder seeder;
	private TransactionTemplate transactionTemplate;
	private Long externalProjectId;

	@BeforeEach
	void createSeeder() {
		transactionTemplate = new TransactionTemplate(transactionManager);
		seeder = new DevDataSeeder(
			userMapper,
			projectMapper,
			projectMemberMapper,
			projectInvitationMapper,
			taskMapper,
			taskLogMapper,
			summaryMapper,
			passwordEncoder,
			MARKER,
			USER_PREFIX,
			"DevSeedTestPassword123!",
			8,
			3,
			4,
			2,
			6,
			2,
			3);
	}

	@AfterEach
	void cleanUp() {
		transactionTemplate.executeWithoutResult(status -> {
			if (externalProjectId != null) {
				taskMapper.delete(Wrappers.<Task>lambdaQuery()
					.eq(Task::getProjectId, externalProjectId));
				projectMemberMapper.delete(Wrappers.<ProjectMember>lambdaQuery()
					.eq(ProjectMember::getProjectId, externalProjectId));
				projectMapper.deleteById(externalProjectId);
			}
			seeder.clearSeedProjects();
			userMapper.delete(Wrappers.<User>lambdaQuery()
				.likeRight(User::getUsername, USER_PREFIX));
		});
	}

	@Test
	void repeatedSeedIsStableAndKeepsRelationshipsValid() {
		transactionTemplate.executeWithoutResult(status -> seeder.seed());
		assertCounts();
		Long externalTaskId = insertExternalTaskReferencingSeedTask();

		transactionTemplate.executeWithoutResult(status -> seeder.seed());
		assertCounts();
		assertRelationships();
		assertThat(taskMapper.selectById(externalTaskId).getParentId()).isNull();
	}

	private Long insertExternalTaskReferencingSeedTask() {
		return transactionTemplate.execute(status -> {
			User owner = seedUsers().getFirst();
			Task seedParent = seedTasks(seedProjects().stream().map(Project::getId).toList()).getFirst();
			Project project = new Project();
			project.setOwnerId(owner.getId());
			project.setName("Seeder cleanup guard project");
			project.setDescription("Non-seed project used to verify parent reference cleanup");
			project.setStatus(ProjectStatus.IN_PROGRESS);
			project.setPriority(Priority.MEDIUM);
			projectMapper.insert(project);
			externalProjectId = project.getId();

			ProjectMember member = new ProjectMember();
			member.setProjectId(project.getId());
			member.setUserId(owner.getId());
			projectMemberMapper.insert(member);

			Task task = new Task();
			task.setProjectId(project.getId());
			task.setParentId(seedParent.getId());
			task.setAssigneeId(owner.getId());
			task.setCreatorId(owner.getId());
			task.setTitle("External task referencing seed parent");
			task.setStatus(TaskStatus.NOT_STARTED);
			task.setPriority(Priority.MEDIUM);
			taskMapper.insert(task);
			return task.getId();
		});
	}

	private void assertCounts() {
		List<Long> projectIds = seedProjects().stream().map(Project::getId).toList();
		List<Long> taskIds = seedTasks(projectIds).stream().map(Task::getId).toList();
		assertThat(seedUsers()).hasSize(8);
		assertThat(projectIds).hasSize(3);
		assertThat(projectMemberMapper.selectCount(Wrappers.<ProjectMember>lambdaQuery()
			.in(ProjectMember::getProjectId, projectIds))).isEqualTo(12);
		assertThat(projectInvitationMapper.selectCount(Wrappers.<ProjectInvitation>lambdaQuery()
			.in(ProjectInvitation::getProjectId, projectIds))).isEqualTo(6);
		assertThat(taskIds).hasSize(18);
		assertThat(taskLogMapper.selectCount(Wrappers.<TaskLog>lambdaQuery()
			.in(TaskLog::getTaskId, taskIds))).isEqualTo(36);
		assertThat(summaryMapper.selectCount(Wrappers.<Summary>lambdaQuery()
			.in(Summary::getProjectId, projectIds))).isEqualTo(9);
	}

	private void assertRelationships() {
		List<User> users = seedUsers();
		List<Project> projects = seedProjects();
		List<Long> projectIds = projects.stream().map(Project::getId).toList();
		List<ProjectMember> members = projectMemberMapper.selectList(Wrappers.<ProjectMember>lambdaQuery()
			.in(ProjectMember::getProjectId, projectIds));
		List<Task> tasks = seedTasks(projectIds);
		Map<Long, Project> projectsById = projects.stream()
			.collect(Collectors.toMap(Project::getId, Function.identity()));
		Map<Long, Task> tasksById = tasks.stream()
			.collect(Collectors.toMap(Task::getId, Function.identity()));
		Map<Long, Set<Long>> memberIdsByProject = members.stream().collect(Collectors.groupingBy(
			ProjectMember::getProjectId,
			Collectors.mapping(ProjectMember::getUserId, Collectors.toSet())));

		assertThat(users).allMatch(user -> user.getSystemRole() == SystemRole.USER && user.isActive());
		assertThat(projects).allMatch(project -> memberIdsByProject.get(project.getId())
			.contains(project.getOwnerId()));
		assertThat(tasks).allMatch(task -> memberIdsByProject.get(task.getProjectId())
			.contains(task.getAssigneeId()));
		assertThat(tasks).allMatch(task -> task.getParentId() == null
			|| tasksById.get(task.getParentId()).getProjectId().equals(task.getProjectId()));

		List<TaskLog> logs = taskLogMapper.selectList(Wrappers.<TaskLog>lambdaQuery()
			.in(TaskLog::getTaskId, tasksById.keySet()));
		assertThat(logs).allMatch(taskLog -> {
			Task task = tasksById.get(taskLog.getTaskId());
			Project project = projectsById.get(task.getProjectId());
			return taskLog.getOperatorId().equals(task.getAssigneeId())
				|| taskLog.getOperatorId().equals(project.getOwnerId());
		});

		List<Summary> summaries = summaryMapper.selectList(Wrappers.<Summary>lambdaQuery()
			.in(Summary::getProjectId, projectIds));
		assertThat(summaries).allMatch(summary -> {
			Project project = projectsById.get(summary.getProjectId());
			return summary.getCreatedBy().equals(project.getOwnerId())
				&& (summary.getTaskId() == null
					|| tasksById.get(summary.getTaskId()).getProjectId().equals(summary.getProjectId()));
		});

		List<ProjectInvitation> invitations = projectInvitationMapper.selectList(
			Wrappers.<ProjectInvitation>lambdaQuery().in(ProjectInvitation::getProjectId, projectIds));
		assertThat(invitations).allMatch(invitation -> {
			boolean isMember = memberIdsByProject.get(invitation.getProjectId())
				.contains(invitation.getInviteeId());
			return invitation.getStatus() == InvitationStatus.ACCEPTED ? isMember : !isMember;
		});
	}

	private List<User> seedUsers() {
		return userMapper.selectList(Wrappers.<User>lambdaQuery()
			.likeRight(User::getUsername, USER_PREFIX));
	}

	private List<Project> seedProjects() {
		return projectMapper.selectList(Wrappers.<Project>lambdaQuery()
			.likeRight(Project::getDescription, MARKER));
	}

	private List<Task> seedTasks(List<Long> projectIds) {
		return taskMapper.selectList(Wrappers.<Task>lambdaQuery()
			.in(Task::getProjectId, projectIds));
	}
}
