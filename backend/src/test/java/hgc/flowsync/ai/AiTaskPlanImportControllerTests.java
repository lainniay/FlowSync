package hgc.flowsync.ai;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import hgc.flowsync.project.Priority;
import hgc.flowsync.project.Project;
import hgc.flowsync.project.ProjectMapper;
import hgc.flowsync.project.ProjectMember;
import hgc.flowsync.project.ProjectMemberMapper;
import hgc.flowsync.project.ProjectStatus;
import hgc.flowsync.task.Task;
import hgc.flowsync.task.TaskMapper;
import hgc.flowsync.user.SystemRole;
import hgc.flowsync.user.User;
import hgc.flowsync.user.UserMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AiTaskPlanImportControllerTests {

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
	@MockitoSpyBean
	private TaskMapper taskMapper;
	@Autowired
	private PasswordEncoder passwordEncoder;
	@Autowired
	private SqlSessionTemplate sqlSessionTemplate;

	private final List<Long> projectIds = new ArrayList<>();
	private final List<Long> userIds = new ArrayList<>();

	@AfterEach
	void deleteCreatedRecords() {
		Mockito.reset(taskMapper);
		if (!projectIds.isEmpty()) {
			taskMapper.update(null, Wrappers.<Task>lambdaUpdate()
				.in(Task::getProjectId, projectIds)
				.set(Task::getParentId, null));
			taskMapper.delete(Wrappers.<Task>lambdaQuery().in(Task::getProjectId, projectIds));
			projectMemberMapper.delete(Wrappers.<ProjectMember>lambdaQuery()
				.in(ProjectMember::getProjectId, projectIds));
			projectMapper.delete(Wrappers.<Project>lambdaQuery().in(Project::getId, projectIds));
		}
		if (!userIds.isEmpty()) {
			userMapper.delete(Wrappers.<User>lambdaQuery().in(User::getId, userIds));
		}
	}

	@Test
	void ownerImportsReviewedTasksInRequestOrder() throws Exception {
		User owner = insertUser(SystemRole.USER, true);
		User assignee = insertUser(SystemRole.USER, true);
		Project project = insertProject(owner);
		addMember(project, assignee);

		MvcResult result = importPlan(login(owner), project, new ImportBody(List.of(
			item("child", "parent", "Child", null, Priority.MEDIUM,
				LocalDate.of(2026, 7, 20), null),
			item("parent", null, "Parent", "Reviewed", Priority.HIGH,
				LocalDate.of(2026, 7, 18), assignee.getId().toString()))))
			.andExpect(status().isCreated())
			.andExpect(content().contentType(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.importedCount").value(2))
			.andExpect(jsonPath("$.tasks.length()").value(2))
			.andExpect(jsonPath("$.tasks[0].title").value("Child"))
			.andExpect(jsonPath("$.tasks[0].status").value("NOT_STARTED"))
			.andExpect(jsonPath("$.tasks[0].progressPercent").value(0))
			.andExpect(jsonPath("$.tasks[0].creator.id").value(owner.getId().toString()))
			.andExpect(jsonPath("$.tasks[1].title").value("Parent"))
			.andExpect(jsonPath("$.tasks[1].assignee.id").value(assignee.getId().toString()))
			.andReturn();

		JsonNode tasks = objectMapper.readTree(result.getResponse().getContentAsByteArray()).get("tasks");
		assertThat(tasks.get(0).get("parentId").asText()).isEqualTo(tasks.get(1).get("id").asText());
		assertThat(taskMapper.selectCount(Wrappers.<Task>lambdaQuery()
			.eq(Task::getProjectId, project.getId()))).isEqualTo(2);
	}

	@Test
	void invalidItemsReportIndexedPathsAndLeaveNoTasks() throws Exception {
		User owner = insertUser(SystemRole.USER, true);
		User outsider = insertUser(SystemRole.USER, true);
		Project project = insertProject(owner);
		LoginSession ownerSession = login(owner);

		importPlan(ownerSession, project, new ImportBody(List.of(
			item("valid", null, "Valid", null, Priority.MEDIUM, null, null),
			item("invalid", null, "Invalid", null, Priority.MEDIUM, null,
				outsider.getId().toString()))))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
			.andExpect(jsonPath("$.errors[0].field").value("items[1].assigneeId"));
		assertNoTasks(project);

		importPlan(ownerSession, project, new ImportBody(List.of(
			item("same", null, "First", null, Priority.MEDIUM, null, null),
			item("same", null, "Second", null, Priority.MEDIUM, null, null))))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.errors[0].field").value("items[1].draftId"));
		assertNoTasks(project);

		importPlan(ownerSession, project, new ImportBody(List.of(
			item("a", "b", "A", null, Priority.MEDIUM, null, null),
			item("b", "a", "B", null, Priority.MEDIUM, null, null))))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.errors[0].field").value("items[1].parentDraftId"));
		assertNoTasks(project);
	}

	@Test
	void onlyActiveOwnerCanImportIntoUnarchivedProject() throws Exception {
		User owner = insertUser(SystemRole.USER, true);
		User member = insertUser(SystemRole.USER, true);
		User admin = insertUser(SystemRole.ADMIN, true);
		Project project = insertProject(owner);
		addMember(project, member);
		ImportBody body = new ImportBody(List.of(
			item("one", null, "One", null, Priority.MEDIUM, null, null)));

		importPlan(login(member), project, body)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));
		importPlan(login(admin), project, body)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));

		project.setArchivedAt(LocalDateTime.now());
		projectMapper.updateById(project);
		importPlan(login(owner), project, body)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("PROJECT_ARCHIVED"));
		assertNoTasks(project);
	}

	@Test
	void databaseFailureRollsBackEveryImportedTask() throws Exception {
		User owner = insertUser(SystemRole.USER, true);
		Project project = insertProject(owner);
		AtomicInteger inserts = new AtomicInteger();
		doAnswer(invocation -> {
			if (inserts.incrementAndGet() == 2) {
				throw new DataIntegrityViolationException("simulated import failure");
			}
			return sqlSessionTemplate.insert(
				TaskMapper.class.getName() + ".insert", invocation.getArgument(0, Task.class));
		}).when(taskMapper).insert(any(Task.class));

		importPlan(login(owner), project, new ImportBody(List.of(
			item("one", null, "One", null, Priority.MEDIUM, null, null),
			item("two", null, "Two", null, Priority.MEDIUM, null, null))))
			.andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));
		assertThat(inserts).hasValue(2);

		Mockito.reset(taskMapper);
		assertNoTasks(project);
	}

	private void assertNoTasks(Project project) {
		assertThat(taskMapper.selectCount(Wrappers.<Task>lambdaQuery()
			.eq(Task::getProjectId, project.getId()))).isZero();
	}

	private ResultActions importPlan(LoginSession session, Project project, ImportBody body)
		throws Exception {
		return mockMvc.perform(post("/api/projects/{projectId}/ai/task-plans/imports", project.getId())
			.session(session.session())
			.header(session.headerName(), session.token())
			.contentType(MediaType.APPLICATION_JSON)
			.content(objectMapper.writeValueAsBytes(body)));
	}

	private LoginSession login(User user) throws Exception {
		MvcResult csrfResult = mockMvc.perform(get("/api/auth/csrf")).andReturn();
		MockHttpSession session = (MockHttpSession) csrfResult.getRequest().getSession(false);
		JsonNode csrf = objectMapper.readTree(csrfResult.getResponse().getContentAsByteArray());
		mockMvc.perform(post("/api/auth/login")
				.session(session)
				.header(csrf.get("headerName").asText(), csrf.get("token").asText())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsBytes(
					new LoginBody(user.getUsername(), "test-password"))))
			.andExpect(status().isOk());
		return new LoginSession(
			session, csrf.get("headerName").asText(), csrf.get("token").asText());
	}

	private User insertUser(SystemRole role, boolean active) {
		User user = new User();
		user.setUsername("ai-import-" + UUID.randomUUID());
		user.setPasswordHash(passwordEncoder.encode("test-password"));
		user.setDisplayName("AI Import User");
		user.setSystemRole(role);
		user.setActive(active);
		userMapper.insert(user);
		userIds.add(user.getId());
		return userMapper.selectById(user.getId());
	}

	private Project insertProject(User owner) {
		Project project = new Project();
		project.setName("AI Import Project");
		project.setStatus(ProjectStatus.NOT_STARTED);
		project.setPriority(Priority.MEDIUM);
		project.setStartDate(LocalDate.of(2026, 7, 1));
		project.setEndDate(LocalDate.of(2026, 8, 31));
		project.setOwnerId(owner.getId());
		projectMapper.insert(project);
		projectIds.add(project.getId());
		addMember(project, owner);
		return project;
	}

	private void addMember(Project project, User user) {
		ProjectMember member = new ProjectMember();
		member.setProjectId(project.getId());
		member.setUserId(user.getId());
		projectMemberMapper.insert(member);
	}

	private static ItemBody item(
		String draftId,
		String parentDraftId,
		String title,
		String description,
		Priority priority,
		LocalDate dueDate,
		String assigneeId) {
		return new ItemBody(
			draftId, parentDraftId, title, description, priority, dueDate, assigneeId);
	}

	record LoginBody(String username, String password) {
	}

	record LoginSession(MockHttpSession session, String headerName, String token) {
	}

	record ImportBody(List<ItemBody> items) {
	}

	record ItemBody(
		String draftId,
		String parentDraftId,
		String title,
		String description,
		Priority priority,
		LocalDate dueDate,
		String assigneeId) {
	}
}
