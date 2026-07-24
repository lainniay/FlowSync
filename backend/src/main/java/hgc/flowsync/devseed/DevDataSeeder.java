package hgc.flowsync.devseed;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
import hgc.flowsync.summary.SummaryType;
import hgc.flowsync.task.Task;
import hgc.flowsync.task.TaskLog;
import hgc.flowsync.task.TaskLogMapper;
import hgc.flowsync.task.TaskMapper;
import hgc.flowsync.task.TaskStatus;
import hgc.flowsync.user.SystemRole;
import hgc.flowsync.user.User;
import hgc.flowsync.user.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

@Component
@Profile("dev-seed")
@Order(100)
@ConditionalOnProperty(name = "flowsync.dev-seed.enabled", havingValue = "true")
public class DevDataSeeder implements ApplicationRunner {

	public static final String DEFAULT_PROJECT_MARKER = "[DEVSEED]";
	public static final String DEFAULT_USERNAME_PREFIX = "devseed-user-";
	private static final int BATCH_SIZE = 500;
	private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

	private final UserMapper userMapper;
	private final ProjectMapper projectMapper;
	private final ProjectMemberMapper projectMemberMapper;
	private final ProjectInvitationMapper projectInvitationMapper;
	private final TaskMapper taskMapper;
	private final TaskLogMapper taskLogMapper;
	private final SummaryMapper summaryMapper;
	private final PasswordEncoder passwordEncoder;
	private final String projectMarker;
	private final String usernamePrefix;
	private final String password;
	private final int userCount;
	private final int projectCount;
	private final int membersPerProject;
	private final int invitationsPerProject;
	private final int tasksPerProject;
	private final int logsPerTask;
	private final int summariesPerProject;

	public DevDataSeeder(
		UserMapper userMapper,
		ProjectMapper projectMapper,
		ProjectMemberMapper projectMemberMapper,
		ProjectInvitationMapper projectInvitationMapper,
		TaskMapper taskMapper,
		TaskLogMapper taskLogMapper,
		SummaryMapper summaryMapper,
		PasswordEncoder passwordEncoder,
		@Value("${flowsync.dev-seed.project-marker:" + DEFAULT_PROJECT_MARKER + "}") String projectMarker,
		@Value("${flowsync.dev-seed.username-prefix:" + DEFAULT_USERNAME_PREFIX + "}") String usernamePrefix,
		@Value("${flowsync.dev-seed.password}") String password,
		@Value("${flowsync.dev-seed.users:100}") int userCount,
		@Value("${flowsync.dev-seed.projects:100}") int projectCount,
		@Value("${flowsync.dev-seed.members-per-project:10}") int membersPerProject,
		@Value("${flowsync.dev-seed.invitations-per-project:3}") int invitationsPerProject,
		@Value("${flowsync.dev-seed.tasks-per-project:50}") int tasksPerProject,
		@Value("${flowsync.dev-seed.logs-per-task:4}") int logsPerTask,
		@Value("${flowsync.dev-seed.summaries-per-project:10}") int summariesPerProject) {
		this.userMapper = userMapper;
		this.projectMapper = projectMapper;
		this.projectMemberMapper = projectMemberMapper;
		this.projectInvitationMapper = projectInvitationMapper;
		this.taskMapper = taskMapper;
		this.taskLogMapper = taskLogMapper;
		this.summaryMapper = summaryMapper;
		this.passwordEncoder = passwordEncoder;
		this.projectMarker = projectMarker;
		this.usernamePrefix = usernamePrefix;
		this.password = password;
		this.userCount = userCount;
		this.projectCount = projectCount;
		this.membersPerProject = membersPerProject;
		this.invitationsPerProject = invitationsPerProject;
		this.tasksPerProject = tasksPerProject;
		this.logsPerTask = logsPerTask;
		this.summariesPerProject = summariesPerProject;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments arguments) {
		seed();
	}

	@Transactional
	public void seed() {
		validateConfiguration();
		clearSeedProjects();
		List<User> users = upsertUsers();
		List<Project> projects = insertProjects(users);
		Map<Long, List<User>> members = insertMembers(projects, users);
		insertInvitations(projects, users, members);
		List<Task> tasks = insertTasks(projects, members);
		insertTaskLogs(tasks, projects);
		insertSummaries(projects, tasks);

		log.info("Dev seed complete: users={}, projects={}, members={}, invitations={}, tasks={}, logs={}, summaries={}",
			userCount,
			projectCount,
			projectCount * membersPerProject,
			projectCount * invitationsPerProject,
			projectCount * tasksPerProject,
			projectCount * tasksPerProject * logsPerTask,
			projectCount * summariesPerProject);
		log.info("Dev seed accounts: {}0001 through {}{}; password uses FLOWSYNC_DEV_SEED_PASSWORD",
			usernamePrefix, usernamePrefix, String.format("%04d", userCount));
	}

	@Transactional
	public void clearSeedProjects() {
		List<Long> projectIds = projectMapper.selectList(Wrappers.<Project>lambdaQuery()
			.select(Project::getId)
			.likeRight(Project::getDescription, projectMarker)).stream()
			.map(Project::getId)
			.toList();
		if (projectIds.isEmpty()) {
			return;
		}

		List<Long> taskIds = taskMapper.selectList(Wrappers.<Task>lambdaQuery()
			.select(Task::getId)
			.in(Task::getProjectId, projectIds)).stream()
			.map(Task::getId)
			.toList();
		summaryMapper.delete(Wrappers.<Summary>lambdaQuery()
			.in(Summary::getProjectId, projectIds));
		if (!taskIds.isEmpty()) {
			taskLogMapper.delete(Wrappers.<TaskLog>lambdaQuery()
				.in(TaskLog::getTaskId, taskIds));
			taskMapper.update(null, Wrappers.<Task>lambdaUpdate()
				.in(Task::getParentId, taskIds)
				.set(Task::getParentId, null));
		}
		taskMapper.delete(Wrappers.<Task>lambdaQuery()
			.in(Task::getProjectId, projectIds));
		projectInvitationMapper.delete(Wrappers.<ProjectInvitation>lambdaQuery()
			.in(ProjectInvitation::getProjectId, projectIds));
		projectMemberMapper.delete(Wrappers.<ProjectMember>lambdaQuery()
			.in(ProjectMember::getProjectId, projectIds));
		projectMapper.deleteByIds(projectIds);
	}

	private List<User> upsertUsers() {
		LinkedHashSet<String> desiredUsernames = new LinkedHashSet<>();
		for (int index = 1; index <= userCount; index++) {
			desiredUsernames.add(seedUsername(index));
		}
		Map<String, User> existingUsers = userMapper.selectList(Wrappers.<User>lambdaQuery()
			.likeRight(User::getUsername, usernamePrefix)).stream()
			.collect(Collectors.toMap(User::getUsername, Function.identity()));
		String passwordHash = passwordEncoder.encode(password);
		List<User> inserts = new ArrayList<>();
		List<User> updates = new ArrayList<>();
		for (int index = 1; index <= userCount; index++) {
			String username = seedUsername(index);
			User user = existingUsers.getOrDefault(username, new User());
			user.setUsername(username);
			user.setPasswordHash(passwordHash);
			user.setDisplayName(String.format("测试用户 %03d", index));
			user.setPhone(String.format("139%08d", index));
			user.setEmail(username + "@example.test");
			user.setSystemRole(SystemRole.USER);
			user.setActive(true);
			if (user.getId() == null) {
				inserts.add(user);
			} else {
				updates.add(user);
			}
		}
		if (!inserts.isEmpty()) {
			userMapper.insert(inserts, BATCH_SIZE);
		}
		if (!updates.isEmpty()) {
			userMapper.updateById(updates, BATCH_SIZE);
		}
		return userMapper.selectList(Wrappers.<User>lambdaQuery()
			.likeRight(User::getUsername, usernamePrefix)
			.orderByAsc(User::getUsername)).stream()
			.filter(user -> desiredUsernames.contains(user.getUsername()))
			.toList();
	}

	private List<Project> insertProjects(List<User> users) {
		LocalDate today = LocalDate.now();
		List<Project> projects = new ArrayList<>(projectCount);
		for (int index = 0; index < projectCount; index++) {
			Project project = new Project();
			project.setOwnerId(users.get(index % users.size()).getId());
			project.setName(String.format("测试项目 %03d", index + 1));
			project.setDescription(projectMarker + " 用于列表、筛选、分页和统计测试");
			project.setStatus(ProjectStatus.values()[index % ProjectStatus.values().length]);
			project.setPriority(Priority.values()[index % Priority.values().length]);
			project.setStartDate(today.minusDays(index % 90L));
			project.setEndDate(project.getStartDate().plusDays(120));
			if ((index + 1) % 10 == 0) {
				project.setArchivedAt(LocalDateTime.now().minusDays(index % 30L));
			}
			projects.add(project);
		}
		projectMapper.insert(projects, BATCH_SIZE);
		return projectMapper.selectList(Wrappers.<Project>lambdaQuery()
			.likeRight(Project::getDescription, projectMarker)
			.orderByAsc(Project::getName));
	}

	private Map<Long, List<User>> insertMembers(List<Project> projects, List<User> users) {
		Map<Long, User> usersById = users.stream()
			.collect(Collectors.toMap(User::getId, Function.identity()));
		Map<Long, List<User>> membersByProject = new java.util.LinkedHashMap<>();
		List<ProjectMember> members = new ArrayList<>(projectCount * membersPerProject);
		for (int projectIndex = 0; projectIndex < projects.size(); projectIndex++) {
			Project project = projects.get(projectIndex);
			LinkedHashSet<Long> memberIds = new LinkedHashSet<>();
			memberIds.add(project.getOwnerId());
			int offset = 0;
			while (memberIds.size() < membersPerProject) {
				memberIds.add(users.get((projectIndex * 7 + offset++) % users.size()).getId());
			}
			List<User> projectUsers = memberIds.stream().map(usersById::get).toList();
			membersByProject.put(project.getId(), projectUsers);
			for (Long userId : memberIds) {
				ProjectMember member = new ProjectMember();
				member.setProjectId(project.getId());
				member.setUserId(userId);
				members.add(member);
			}
		}
		projectMemberMapper.insert(members, BATCH_SIZE);
		return membersByProject;
	}

	private void insertInvitations(
		List<Project> projects,
		List<User> users,
		Map<Long, List<User>> membersByProject) {
		List<ProjectInvitation> invitations = new ArrayList<>(projectCount * invitationsPerProject);
		InvitationStatus[] statuses = InvitationStatus.values();
		for (int projectIndex = 0; projectIndex < projects.size(); projectIndex++) {
			Project project = projects.get(projectIndex);
			List<User> members = membersByProject.get(project.getId());
			LinkedHashSet<Long> usedInvitees = new LinkedHashSet<>();
			for (int invitationIndex = 0; invitationIndex < invitationsPerProject; invitationIndex++) {
				InvitationStatus status = statuses[(projectIndex + invitationIndex) % statuses.length];
				User invitee;
				if (status == InvitationStatus.ACCEPTED) {
					invitee = members.stream()
						.filter(member -> !member.getId().equals(project.getOwnerId()))
						.filter(member -> !usedInvitees.contains(member.getId()))
						.findFirst()
						.orElseThrow();
				} else {
					invitee = users.stream()
						.filter(user -> members.stream().noneMatch(member -> member.getId().equals(user.getId())))
						.filter(user -> !usedInvitees.contains(user.getId()))
						.findFirst()
						.orElseThrow();
				}
				usedInvitees.add(invitee.getId());
				ProjectInvitation invitation = new ProjectInvitation();
				invitation.setProjectId(project.getId());
				invitation.setInviteeId(invitee.getId());
				invitation.setInvitedBy(project.getOwnerId());
				invitation.setStatus(status);
				if (status != InvitationStatus.PENDING) {
					invitation.setRespondedAt(LocalDateTime.now().minusDays(invitationIndex + 1L));
				}
				invitations.add(invitation);
			}
		}
		projectInvitationMapper.insert(invitations, BATCH_SIZE);
	}

	private List<Task> insertTasks(List<Project> projects, Map<Long, List<User>> membersByProject) {
		int rootCount = Math.min(10, Math.max(1, tasksPerProject / 5));
		List<Task> roots = new ArrayList<>(projectCount * rootCount);
		for (Project project : projects) {
			List<User> members = membersByProject.get(project.getId());
			for (int index = 0; index < rootCount; index++) {
				roots.add(task(project, members, index, null));
			}
		}
		taskMapper.insert(roots, BATCH_SIZE);
		Map<Long, List<Task>> rootsByProject = taskMapper.selectList(Wrappers.<Task>lambdaQuery()
			.in(Task::getProjectId, projects.stream().map(Project::getId).toList())
			.isNull(Task::getParentId)
			.orderByAsc(Task::getTitle)).stream()
			.collect(Collectors.groupingBy(Task::getProjectId));

		List<Task> children = new ArrayList<>(projectCount * (tasksPerProject - rootCount));
		for (Project project : projects) {
			List<User> members = membersByProject.get(project.getId());
			List<Task> projectRoots = rootsByProject.get(project.getId());
			for (int index = rootCount; index < tasksPerProject; index++) {
				children.add(task(project, members, index, projectRoots.get(index % rootCount).getId()));
			}
		}
		if (!children.isEmpty()) {
			taskMapper.insert(children, BATCH_SIZE);
		}
		return taskMapper.selectList(Wrappers.<Task>lambdaQuery()
			.in(Task::getProjectId, projects.stream().map(Project::getId).toList())
			.orderByAsc(Task::getProjectId, Task::getTitle));
	}

	private Task task(Project project, List<User> members, int index, Long parentId) {
		Task task = new Task();
		task.setProjectId(project.getId());
		task.setParentId(parentId);
		task.setAssigneeId(members.get(index % members.size()).getId());
		task.setCreatorId(project.getOwnerId());
		task.setTitle(String.format("任务 %03d - %03d", project.getId(), index + 1));
		task.setDescription("用于验证任务列表、负责人、状态、优先级和父子任务展示");
		task.setStatus(TaskStatus.values()[index % TaskStatus.values().length]);
		task.setPriority(Priority.values()[index % Priority.values().length]);
		task.setDueDate(project.getStartDate().plusDays((index * 7L) % 121));
		return task;
	}

	private void insertTaskLogs(List<Task> tasks, List<Project> projects) {
		Map<Long, Project> projectsById = projects.stream()
			.collect(Collectors.toMap(Project::getId, Function.identity()));
		List<TaskLog> logs = new ArrayList<>(tasks.size() * logsPerTask);
		for (Task task : tasks) {
			for (int index = 0; index < logsPerTask; index++) {
				TaskLog taskLog = new TaskLog();
				taskLog.setTaskId(task.getId());
				taskLog.setOperatorId(index % 2 == 0
					? projectsById.get(task.getProjectId()).getOwnerId()
					: task.getAssigneeId());
				int progress = Math.min(100, (index + 1) * (100 / Math.max(1, logsPerTask)));
				if (task.getStatus() == TaskStatus.COMPLETED && index == logsPerTask - 1) {
					progress = 100;
				}
				taskLog.setProgressPercent(progress);
				taskLog.setContent(String.format("第 %d 次进度更新，当前完成度 %d%%", index + 1, progress));
				logs.add(taskLog);
			}
		}
		taskLogMapper.insert(logs, BATCH_SIZE);
	}

	private void insertSummaries(List<Project> projects, List<Task> tasks) {
		Map<Long, List<Task>> tasksByProject = tasks.stream()
			.collect(Collectors.groupingBy(Task::getProjectId));
		List<Summary> summaries = new ArrayList<>(projectCount * summariesPerProject);
		for (Project project : projects) {
			List<Task> projectTasks = tasksByProject.get(project.getId());
			for (int index = 0; index < summariesPerProject; index++) {
				Summary summary = new Summary();
				summary.setProjectId(project.getId());
				summary.setCreatedBy(project.getOwnerId());
				if (index < summariesPerProject * 7 / 10) {
					summary.setTaskId(projectTasks.get(index % projectTasks.size()).getId());
					summary.setType(SummaryType.STAGE);
				} else {
					summary.setType(SummaryType.FINAL);
				}
				summary.setContent(String.format("测试总结 %02d：记录项目阶段成果、风险和下一步计划", index + 1));
				summaries.add(summary);
			}
		}
		summaryMapper.insert(summaries, BATCH_SIZE);
	}

	private void validateConfiguration() {
		Assert.hasText(password, "flowsync.dev-seed.password must not be blank");
		Assert.isTrue(userCount > membersPerProject + invitationsPerProject,
			"dev seed users must exceed members and invitations per project");
		Assert.isTrue(projectCount > 0, "dev seed project count must be positive");
		Assert.isTrue(membersPerProject > 1, "dev seed needs at least two members per project");
		Assert.isTrue(tasksPerProject > 0, "dev seed task count must be positive");
		Assert.isTrue(logsPerTask > 0, "dev seed log count must be positive");
		Assert.isTrue(summariesPerProject > 0, "dev seed summary count must be positive");
	}

	private String seedUsername(int index) {
		return usernamePrefix + String.format("%04d", index);
	}
}
