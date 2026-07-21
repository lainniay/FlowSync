package hgc.flowsync.project;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProjectLifecycleControllerTests {

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
	private ProjectInvitationMapper projectInvitationMapper;
	@Autowired
	private TaskMapper taskMapper;
	@Autowired
	private TaskLogMapper taskLogMapper;
	@Autowired
	private SummaryMapper summaryMapper;
	@Autowired
	private JdbcTemplate jdbcTemplate;
	@Autowired
	private PasswordEncoder passwordEncoder;

	@Test
	void projectListAppliesVisibilityFiltersPaginationAndStatistics() throws Exception {
		User owner = insertUser(SystemRole.USER, true);
		User member = insertUser(SystemRole.USER, true);
		User outsider = insertUser(SystemRole.USER, true);
		User admin = insertUser(SystemRole.ADMIN, true);
		Project project = insertProject(owner, "Lifecycle Visible", null);
		insertMember(project, member);
		insertTask(project, owner, TaskStatus.COMPLETED);
		insertTask(project, member, TaskStatus.IN_PROGRESS);
		insertProject(owner, "Lifecycle Archived", LocalDateTime.now());

		mockMvc.perform(get("/api/projects")
				.session(login(member).session()))
			.andExpect(status().isOk())
			.andExpect(content().contentType(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.items[0].id").value(project.getId().toString()))
			.andExpect(jsonPath("$.items[0].memberCount").value(2))
			.andExpect(jsonPath("$.items[0].taskStats.total").value(2))
			.andExpect(jsonPath("$.items[0].taskStats.completed").value(1))
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.totalPages").value(1));
		mockMvc.perform(get("/api/projects")
				.session(login(outsider).session()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items").isEmpty())
			.andExpect(jsonPath("$.totalElements").value(0));
		mockMvc.perform(get("/api/projects")
				.param("q", "Visible")
				.param("status", "NOT_STARTED")
				.param("ownerId", owner.getId().toString())
				.param("size", "1")
				.param("sort", "name,asc")
				.session(login(admin).session()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].name").value("Lifecycle Visible"));
		mockMvc.perform(get("/api/projects")
				.param("archived", "true")
				.session(login(admin).session()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].name").value("Lifecycle Archived"))
			.andExpect(jsonPath("$.items[0].archivedAt").isNotEmpty());
		mockMvc.perform(get("/api/projects")
				.param("sort", "invalid,asc")
				.session(login(admin).session()))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		mockMvc.perform(get("/api/projects")
				.param("archived", "yes")
				.session(login(admin).session()))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	@Test
	void projectDetailEnforcesMemberAdminVisibility() throws Exception {
		User owner = insertUser(SystemRole.USER, true);
		User member = insertUser(SystemRole.USER, true);
		User outsider = insertUser(SystemRole.USER, true);
		User admin = insertUser(SystemRole.ADMIN, true);
		Project project = insertProject(owner, "Lifecycle Detail", null);
		insertMember(project, member);

		getProject(login(member), project.getId())
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.owner.id").value(owner.getId().toString()));
		getProject(login(admin), project.getId()).andExpect(status().isOk());
		getProject(login(outsider), project.getId())
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOT_FOUND"));
		getProject(login(admin), Long.MAX_VALUE)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOT_FOUND"));
		mockMvc.perform(get("/api/projects/{projectId}", project.getId()))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void projectUpdateRejectsDateRangeExcludingExistingTask() throws Exception {
		User owner = insertUser(SystemRole.USER, true);
		Project project = insertProject(owner, "Lifecycle Date Range", null);
		Task task = insertTask(project, owner, TaskStatus.IN_PROGRESS);
		task.setDueDate(LocalDate.of(2026, 7, 15));
		taskMapper.updateById(task);

		update(login(owner), project, new UpdateBody(
			project.getName(), project.getDescription(), project.getStatus(), project.getPriority(),
			LocalDate.of(2026, 7, 16), project.getEndDate()))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

		Project unchanged = projectMapper.selectById(project.getId());
		assertThat(unchanged.getStartDate()).isEqualTo(LocalDate.of(2026, 7, 1));
		assertThat(unchanged.getEndDate()).isEqualTo(LocalDate.of(2026, 8, 31));
	}

	@Test
	void ownerAndAdminFullyUpdateOnlyUnarchivedProjects() throws Exception {
		User owner = insertUser(SystemRole.USER, true);
		User member = insertUser(SystemRole.USER, true);
		User admin = insertUser(SystemRole.ADMIN, true);
		Project project = insertProject(owner, "Lifecycle Update", null);
		insertMember(project, member);
		UpdateBody cleared = new UpdateBody(
			"Lifecycle Updated", null, ProjectStatus.IN_PROGRESS, Priority.HIGH, null, null);

		update(login(member), project, cleared)
			.andExpect(status().isForbidden());
		update(login(owner), project, cleared)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Lifecycle Updated"))
			.andExpect(jsonPath("$.description").value((Object) null))
			.andExpect(jsonPath("$.startDate").value((Object) null))
			.andExpect(jsonPath("$.endDate").value((Object) null));

		Project saved = projectMapper.selectById(project.getId());
		assertThat(saved.getDescription()).isNull();
		assertThat(saved.getStartDate()).isNull();
		assertThat(saved.getEndDate()).isNull();
		update(login(admin), project, new UpdateBody(
			"Admin Updated", "managed", ProjectStatus.COMPLETED, Priority.LOW,
			LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Admin Updated"));
		update(login(owner), project, new UpdateBody(
			"Invalid Dates", null, ProjectStatus.IN_PROGRESS, Priority.MEDIUM,
			LocalDate.of(2026, 8, 1), LocalDate.of(2026, 7, 31)))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		LoginSession ownerSession = login(owner);
		mockMvc.perform(put("/api/projects/{projectId}", project.getId())
				.session(ownerSession.session())
				.header(ownerSession.headerName(), ownerSession.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"name":"Missing Description","status":"IN_PROGRESS","priority":"MEDIUM",
					 "startDate":null,"endDate":null}
					"""))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
			.andExpect(jsonPath("$.errors[0].field").value("description"));

		projectMapper.update(null, com.baomidou.mybatisplus.core.toolkit.Wrappers.<Project>lambdaUpdate()
			.eq(Project::getId, project.getId())
			.set(Project::getArchivedAt, LocalDateTime.now()));
		update(login(owner), project, cleared)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("PROJECT_ARCHIVED"));
	}

	@Test
	void ownerTransferAddsMemberAndCancelsPendingInvitation() throws Exception {
		User owner = insertUser(SystemRole.USER, true);
		User target = insertUser(SystemRole.USER, true);
		User inactive = insertUser(SystemRole.USER, false);
		User adminAccount = insertUser(SystemRole.ADMIN, true);
		Project project = insertProject(owner, "Lifecycle Transfer", null);
		ProjectInvitation invitation = insertInvitation(project, target, owner);

		transfer(login(target), project, owner.getId().toString())
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));
		transfer(login(owner), project, target.getId().toString())
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.owner.id").value(target.getId().toString()))
			.andExpect(jsonPath("$.memberCount").value(2));
		assertThat(projectMapper.selectById(project.getId()).getOwnerId()).isEqualTo(target.getId());
		assertThat(projectMemberMapper.existsByProjectIdAndUserId(project.getId(), owner.getId())).isTrue();
		assertThat(projectMemberMapper.existsByProjectIdAndUserId(project.getId(), target.getId())).isTrue();
		ProjectInvitation cancelled = projectInvitationMapper.selectById(invitation.getId());
		assertThat(cancelled.getStatus()).isEqualTo(InvitationStatus.CANCELLED);
		assertThat(cancelled.getRespondedAt()).isNotNull();

		transfer(login(target), project, inactive.getId().toString())
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		transfer(login(target), project, adminAccount.getId().toString())
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		transfer(login(target), project, Long.toString(Long.MAX_VALUE))
			.andExpect(status().isNotFound());
		transfer(login(adminAccount), project, owner.getId().toString())
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.owner.id").value(owner.getId().toString()));
	}

	@Test
	void archiveAndRestoreEnforceStatePermissionAndCsrf() throws Exception {
		User owner = insertUser(SystemRole.USER, true);
		User member = insertUser(SystemRole.USER, true);
		User admin = insertUser(SystemRole.ADMIN, true);
		Project project = insertProject(owner, "Lifecycle Archive", null);
		insertMember(project, member);

		writeWithoutBody(put("/api/projects/{projectId}/archive", project.getId()), login(member))
			.andExpect(status().isForbidden());
		LoginSession ownerSession = login(owner);
		writeWithoutBody(put("/api/projects/{projectId}/archive", project.getId()), ownerSession)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.archivedAt").isNotEmpty());
		writeWithoutBody(put("/api/projects/{projectId}/archive", project.getId()), ownerSession)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("PROJECT_ARCHIVED"));
		writeWithoutBody(delete("/api/projects/{projectId}/archive", project.getId()), login(admin))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.archivedAt").value((Object) null));
		writeWithoutBody(delete("/api/projects/{projectId}/archive", project.getId()), ownerSession)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("PROJECT_NOT_ARCHIVED"));
		mockMvc.perform(put("/api/projects/{projectId}/archive", project.getId())
				.session(ownerSession.session()))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("CSRF_INVALID"));
	}

	@Test
	void ownerPermanentlyDeletesOnlyArchivedProjectAggregate() throws Exception {
		User owner = insertUser(SystemRole.USER, true);
		User member = insertUser(SystemRole.USER, true);
		User outsider = insertUser(SystemRole.USER, true);
		User invitee = insertUser(SystemRole.USER, true);
		Project project = insertProject(owner, "Lifecycle Delete", null);
		insertMember(project, member);
		ProjectInvitation invitation = insertInvitation(project, invitee, owner);
		Task task = insertTask(project, member, TaskStatus.COMPLETED);
		Task childTask = insertTask(project, member, TaskStatus.COMPLETED);
		childTask.setParentId(task.getId());
		taskMapper.updateById(childTask);
		TaskLog taskLog = insertTaskLog(task, member);
		Summary summary = insertSummary(project, task, owner);

		writeWithoutBody(delete("/api/projects/{projectId}", project.getId()), login(outsider))
			.andExpect(status().isForbidden());
		writeWithoutBody(delete("/api/projects/{projectId}", project.getId()), login(owner))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("PROJECT_NOT_ARCHIVED"));
		project.setArchivedAt(LocalDateTime.now());
		projectMapper.updateById(project);
		writeWithoutBody(delete("/api/projects/{projectId}", project.getId()), login(owner))
			.andExpect(status().isNoContent())
			.andExpect(content().string(""));

		assertThat(summaryMapper.selectById(summary.getId())).isNull();
		assertThat(taskLogMapper.selectById(taskLog.getId())).isNull();
		assertThat(taskMapper.selectById(task.getId())).isNull();
		assertThat(taskMapper.selectById(childTask.getId())).isNull();
		assertThat(projectInvitationMapper.selectById(invitation.getId())).isNull();
		assertThat(projectMemberMapper.selectCount(
			com.baomidou.mybatisplus.core.toolkit.Wrappers.<ProjectMember>lambdaQuery()
				.eq(ProjectMember::getProjectId, project.getId()))).isZero();
		assertThat(projectMapper.selectById(project.getId())).isNull();
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void projectDeleteConstraintFailureReturnsResourceInUseAndRollsBack() throws Exception {
		jdbcTemplate.execute("DROP TABLE IF EXISTS project_delete_guard");
		User owner = null;
		Project project = null;
		Task task = null;
		TaskLog taskLog = null;
		Summary summary = null;
		try {
			owner = insertUser(SystemRole.USER, true);
			project = insertProject(owner, "Lifecycle Delete Rollback", LocalDateTime.now());
			task = insertTask(project, owner, TaskStatus.IN_PROGRESS);
			taskLog = insertTaskLog(task, owner);
			summary = insertSummary(project, task, owner);
			jdbcTemplate.execute("""
				CREATE TABLE project_delete_guard (
					project_id BIGINT NOT NULL PRIMARY KEY,
					CONSTRAINT fk_project_delete_guard_project FOREIGN KEY (project_id)
						REFERENCES projects (id) ON DELETE RESTRICT ON UPDATE RESTRICT
				) ENGINE = InnoDB
				""");
			jdbcTemplate.update("INSERT INTO project_delete_guard (project_id) VALUES (?)", project.getId());

			writeWithoutBody(delete("/api/projects/{projectId}", project.getId()), login(owner))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("RESOURCE_IN_USE"));
			assertThat(projectMapper.selectById(project.getId())).isNotNull();
			assertThat(projectMemberMapper.existsByProjectIdAndUserId(project.getId(), owner.getId())).isTrue();
			assertThat(taskMapper.selectById(task.getId())).isNotNull();
			assertThat(taskLogMapper.selectById(taskLog.getId())).isNotNull();
			assertThat(summaryMapper.selectById(summary.getId())).isNotNull();
		} finally {
			jdbcTemplate.execute("DROP TABLE IF EXISTS project_delete_guard");
			if (project != null) {
				summaryMapper.delete(com.baomidou.mybatisplus.core.toolkit.Wrappers.<Summary>lambdaQuery()
					.eq(Summary::getProjectId, project.getId()));
				if (task != null) {
					taskLogMapper.delete(com.baomidou.mybatisplus.core.toolkit.Wrappers.<TaskLog>lambdaQuery()
						.eq(TaskLog::getTaskId, task.getId()));
				}
				taskMapper.delete(com.baomidou.mybatisplus.core.toolkit.Wrappers.<Task>lambdaQuery()
					.eq(Task::getProjectId, project.getId()));
				projectMemberMapper.delete(com.baomidou.mybatisplus.core.toolkit.Wrappers.<ProjectMember>lambdaQuery()
					.eq(ProjectMember::getProjectId, project.getId()));
				projectMapper.deleteById(project.getId());
			}
			if (owner != null) {
				userMapper.deleteById(owner.getId());
			}
		}
	}

	private ResultActions getProject(LoginSession session, Long projectId) throws Exception {
		return mockMvc.perform(get("/api/projects/{projectId}", projectId).session(session.session()));
	}

	private ResultActions update(LoginSession session, Project project, UpdateBody body) throws Exception {
		return mockMvc.perform(put("/api/projects/{projectId}", project.getId())
			.session(session.session())
			.header(session.headerName(), session.token())
			.contentType(MediaType.APPLICATION_JSON)
			.content(objectMapper.writeValueAsBytes(body)));
	}

	private ResultActions transfer(
		LoginSession session,
		Project project,
		String ownerId) throws Exception {
		return mockMvc.perform(put("/api/projects/{projectId}/owner", project.getId())
			.session(session.session())
			.header(session.headerName(), session.token())
			.contentType(MediaType.APPLICATION_JSON)
			.content(objectMapper.writeValueAsBytes(new TransferBody(ownerId))));
	}

	private ResultActions writeWithoutBody(
		org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
		LoginSession session) throws Exception {
		return mockMvc.perform(request
			.session(session.session())
			.header(session.headerName(), session.token()));
	}

	private LoginSession login(User user) throws Exception {
		MvcResult csrfResult = mockMvc.perform(get("/api/auth/csrf")).andReturn();
		JsonNode csrf = objectMapper.readTree(csrfResult.getResponse().getContentAsByteArray());
		MockHttpSession session = (MockHttpSession) csrfResult.getRequest().getSession(false);
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
				.post("/api/auth/login")
				.session(session)
				.header(csrf.get("headerName").asText(), csrf.get("token").asText())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsBytes(
					new LoginBody(user.getUsername(), "test-password"))))
			.andExpect(status().isOk());
		return new LoginSession(session, csrf.get("headerName").asText(), csrf.get("token").asText());
	}

	private User insertUser(SystemRole role, boolean active) {
		User user = new User();
		user.setUsername("lifecycle-" + UUID.randomUUID());
		user.setPasswordHash(passwordEncoder.encode("test-password"));
		user.setDisplayName("Lifecycle User");
		user.setSystemRole(role);
		user.setActive(active);
		userMapper.insert(user);
		return userMapper.selectById(user.getId());
	}

	private Project insertProject(User owner, String name, LocalDateTime archivedAt) {
		Project project = new Project();
		project.setOwnerId(owner.getId());
		project.setName(name);
		project.setDescription("description");
		project.setStatus(ProjectStatus.NOT_STARTED);
		project.setPriority(Priority.MEDIUM);
		project.setStartDate(LocalDate.of(2026, 7, 1));
		project.setEndDate(LocalDate.of(2026, 8, 31));
		project.setArchivedAt(archivedAt);
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

	private Task insertTask(Project project, User assignee, TaskStatus status) {
		Task task = new Task();
		task.setProjectId(project.getId());
		task.setAssigneeId(assignee.getId());
		task.setCreatorId(project.getOwnerId());
		task.setTitle("Lifecycle Task " + UUID.randomUUID());
		task.setStatus(status);
		task.setPriority(Priority.MEDIUM);
		taskMapper.insert(task);
		return taskMapper.selectById(task.getId());
	}

	private TaskLog insertTaskLog(Task task, User operator) {
		TaskLog taskLog = new TaskLog();
		taskLog.setTaskId(task.getId());
		taskLog.setOperatorId(operator.getId());
		taskLog.setProgressPercent(100);
		taskLog.setContent("completed");
		taskLogMapper.insert(taskLog);
		return taskLogMapper.selectById(taskLog.getId());
	}

	private Summary insertSummary(Project project, Task task, User creator) {
		Summary summary = new Summary();
		summary.setProjectId(project.getId());
		summary.setTaskId(task.getId());
		summary.setCreatedBy(creator.getId());
		summary.setType(SummaryType.STAGE);
		summary.setContent("summary");
		summaryMapper.insert(summary);
		return summaryMapper.selectById(summary.getId());
	}

	private ProjectInvitation insertInvitation(Project project, User invitee, User inviter) {
		ProjectInvitation invitation = new ProjectInvitation();
		invitation.setProjectId(project.getId());
		invitation.setInviteeId(invitee.getId());
		invitation.setInvitedBy(inviter.getId());
		invitation.setStatus(InvitationStatus.PENDING);
		projectInvitationMapper.insert(invitation);
		return projectInvitationMapper.selectById(invitation.getId());
	}

	record LoginBody(String username, String password) {
	}

	record UpdateBody(
		String name,
		String description,
		ProjectStatus status,
		Priority priority,
		LocalDate startDate,
		LocalDate endDate) {
	}

	record TransferBody(String ownerId) {
	}

	record LoginSession(MockHttpSession session, String headerName, String token) {
	}
}
