package hgc.flowsync.overview;

import java.time.LocalDate;
import java.util.UUID;

import hgc.flowsync.common.time.ApiDateTime;
import hgc.flowsync.project.Priority;
import hgc.flowsync.project.Project;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.contains;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OverviewControllerTests {

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private ObjectMapper objectMapper;
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
	@Autowired
	private PasswordEncoder passwordEncoder;

	@Test
	void memberOverviewCountsOnlyVisibleProjectsAndBuildsActivities() throws Exception {
		User owner = insertUser(SystemRole.USER);
		User member = insertUser(SystemRole.USER);
		User otherOwner = insertUser(SystemRole.USER);
		Project visible = insertProject(owner, "Visible Overview");
		insertMember(visible, member);
		Project hidden = insertProject(otherOwner, "Hidden Overview");
		Task completed = insertTask(visible, member, TaskStatus.COMPLETED, ApiDateTime.today().minusDays(2));
		Task overdue = insertTask(visible, member, TaskStatus.IN_PROGRESS, ApiDateTime.today().minusDays(1));
		insertTask(visible, member, TaskStatus.CANCELLED, ApiDateTime.today().minusDays(1));
		insertTask(hidden, otherOwner, TaskStatus.BLOCKED, null);
		TaskLog taskLog = insertTaskLog(overdue, member);
		insertSummary(visible, completed, owner);

		mockMvc.perform(get("/api/overview").session(login(member)))
			.andExpect(status().isOk())
			.andExpect(content().contentType(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.counts.projects").value(1))
			.andExpect(jsonPath("$.counts.tasks").value(3))
			.andExpect(jsonPath("$.counts.completedTasks").value(1))
			.andExpect(jsonPath("$.counts.overdueTasks").value(1))
			.andExpect(jsonPath("$.counts.summaries").value(1))
			.andExpect(jsonPath("$.counts.members").value(2))
			.andExpect(jsonPath("$.tasksByStatus.length()").value(5))
			.andExpect(jsonPath("$.tasksByStatus[0].status").value("NOT_STARTED"))
			.andExpect(jsonPath("$.tasksByStatus[1].count").value(1))
			.andExpect(jsonPath("$.tasksByStatus[3].count").value(1))
			.andExpect(jsonPath("$.tasksByStatus[4].count").value(1))
			.andExpect(jsonPath("$.recentActivities.length()").value(6))
			.andExpect(jsonPath("$.recentActivities[?(@.type == 'PROJECT_CREATED')]").exists())
			.andExpect(jsonPath("$.recentActivities[?(@.type == 'TASK_CREATED')]").exists())
			.andExpect(jsonPath("$.recentActivities[?(@.type == 'TASK_PROGRESS_ADDED')]").exists())
			.andExpect(jsonPath("$.recentActivities[?(@.type == 'TASK_PROGRESS_ADDED')].resourceId")
				.value(contains(taskLog.getId().toString())))
			.andExpect(jsonPath("$.recentActivities[?(@.type == 'SUMMARY_CREATED')]").exists())
			.andExpect(jsonPath("$.recentActivities[0].occurredAt").isNotEmpty());
	}

	@Test
	void recentActivitiesAreLimitedToTen() throws Exception {
		User owner = insertUser(SystemRole.USER);
		Project project = insertProject(owner, "Busy Overview");
		for (int index = 0; index < 12; index++) {
			insertTask(project, owner, TaskStatus.NOT_STARTED, null);
		}

		mockMvc.perform(get("/api/overview").session(login(owner)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.counts.tasks").value(12))
			.andExpect(jsonPath("$.tasksByStatus[0].count").value(12))
			.andExpect(jsonPath("$.recentActivities.length()").value(10));
	}

	@Test
	void adminCanFilterAnyProjectWhileUsersCannotFilterInvisibleProjects() throws Exception {
		User owner = insertUser(SystemRole.USER);
		User outsider = insertUser(SystemRole.USER);
		User admin = insertUser(SystemRole.ADMIN);
		Project project = insertProject(owner, "Filtered Overview");
		insertTask(project, owner, TaskStatus.NOT_STARTED, null);

		mockMvc.perform(get("/api/overview")
				.param("projectId", project.getId().toString())
				.session(login(admin)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.counts.projects").value(1))
			.andExpect(jsonPath("$.counts.tasks").value(1));
		mockMvc.perform(get("/api/overview")
				.param("projectId", project.getId().toString())
				.session(login(outsider)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOT_FOUND"));
		mockMvc.perform(get("/api/overview")
				.param("projectId", Long.toString(Long.MAX_VALUE))
				.session(login(admin)))
			.andExpect(status().isNotFound());
	}

	@Test
	void emptyOverviewAndRequestValidationFollowTheContract() throws Exception {
		User user = insertUser(SystemRole.USER);
		MockHttpSession session = login(user);

		mockMvc.perform(get("/api/overview").session(session))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.counts.projects").value(0))
			.andExpect(jsonPath("$.tasksByStatus.length()").value(5))
			.andExpect(jsonPath("$.recentActivities").isEmpty());
		mockMvc.perform(get("/api/overview").param("projectId", "0").session(session))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		mockMvc.perform(get("/api/overview"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	private MockHttpSession login(User user) throws Exception {
		MvcResult csrfResult = mockMvc.perform(get("/api/auth/csrf")).andReturn();
		JsonNode csrf = objectMapper.readTree(csrfResult.getResponse().getContentAsByteArray());
		MockHttpSession session = (MockHttpSession) csrfResult.getRequest().getSession(false);
		mockMvc.perform(post("/api/auth/login")
				.session(session)
				.header(csrf.get("headerName").asText(), csrf.get("token").asText())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsBytes(new LoginBody(user.getUsername(), "test-password"))))
			.andExpect(status().isOk());
		return session;
	}

	private User insertUser(SystemRole role) {
		User user = new User();
		user.setUsername("overview-" + UUID.randomUUID());
		user.setPasswordHash(passwordEncoder.encode("test-password"));
		user.setDisplayName("Overview User");
		user.setSystemRole(role);
		user.setActive(true);
		userMapper.insert(user);
		return userMapper.selectById(user.getId());
	}

	private Project insertProject(User owner, String name) {
		Project project = new Project();
		project.setOwnerId(owner.getId());
		project.setName(name);
		project.setStatus(ProjectStatus.NOT_STARTED);
		project.setPriority(Priority.MEDIUM);
		projectMapper.insert(project);
		insertMember(project, owner);
		return projectMapper.selectById(project.getId());
	}

	private void insertMember(Project project, User user) {
		ProjectMember member = new ProjectMember();
		member.setProjectId(project.getId());
		member.setUserId(user.getId());
		projectMemberMapper.insert(member);
	}

	private Task insertTask(Project project, User assignee, TaskStatus status, LocalDate dueDate) {
		Task task = new Task();
		task.setProjectId(project.getId());
		task.setAssigneeId(assignee.getId());
		task.setCreatorId(project.getOwnerId());
		task.setTitle("Overview Task " + UUID.randomUUID());
		task.setStatus(status);
		task.setPriority(Priority.MEDIUM);
		task.setDueDate(dueDate);
		taskMapper.insert(task);
		return taskMapper.selectById(task.getId());
	}

	private TaskLog insertTaskLog(Task task, User operator) {
		TaskLog taskLog = new TaskLog();
		taskLog.setTaskId(task.getId());
		taskLog.setOperatorId(operator.getId());
		taskLog.setProgressPercent(50);
		taskLog.setContent("Half complete");
		taskLogMapper.insert(taskLog);
		return taskLog;
	}

	private void insertSummary(Project project, Task task, User creator) {
		Summary summary = new Summary();
		summary.setProjectId(project.getId());
		summary.setTaskId(task.getId());
		summary.setCreatedBy(creator.getId());
		summary.setType(SummaryType.STAGE);
		summary.setContent("Overview summary");
		summaryMapper.insert(summary);
	}

	record LoginBody(String username, String password) {
	}
}
