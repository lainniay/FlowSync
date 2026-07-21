package hgc.flowsync.ai;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

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
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.ArgumentCaptor;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
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
	private TaskLogMapper taskLogMapper;
	@MockitoBean
	private OpenAiCompatibleClient aiClient;
	@Autowired
	private PasswordEncoder passwordEncoder;
	@Autowired
	private AiGenerationService generationService;
	@Autowired
	private SqlSessionTemplate sqlSessionTemplate;

	private final List<Long> projectIds = new ArrayList<>();
	private final List<Long> userIds = new ArrayList<>();

	@AfterEach
	void deleteCreatedRecords() {
		Mockito.reset(taskMapper);
		Mockito.reset(aiClient);
		if (!projectIds.isEmpty()) {
			List<Long> taskIds = taskMapper.selectList(Wrappers.<Task>lambdaQuery()
				.select(Task::getId)
				.in(Task::getProjectId, projectIds)).stream().map(Task::getId).toList();
			if (!taskIds.isEmpty()) {
				taskLogMapper.delete(Wrappers.<TaskLog>lambdaQuery().in(TaskLog::getTaskId, taskIds));
			}
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
	void ownerAndAssigneeReceiveTransientTaskSuggestions() throws Exception {
		User owner = insertUser(SystemRole.USER, true);
		User assignee = insertUser(SystemRole.USER, true);
		Project project = insertProject(owner);
		addMember(project, assignee);
		Task task = insertTask(project, owner, assignee);
		when(aiClient.generateSuggestion(anyString(), anyString())).thenReturn("Review the integration.");

		suggest(login(owner), task, "API integration")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.suggestion").value("Review the integration."))
			.andExpect(jsonPath("$.generatedAt").isString());
		suggest(login(assignee), task, null)
			.andExpect(status().isOk());

		assertThat(taskLogMapper.selectCount(Wrappers.<TaskLog>lambdaQuery()
			.eq(TaskLog::getTaskId, task.getId()))).isZero();
	}

	@Test
	void taskSuggestionEnforcesRoleAssignmentAndArchiveRules() throws Exception {
		User owner = insertUser(SystemRole.USER, true);
		User assignee = insertUser(SystemRole.USER, true);
		User member = insertUser(SystemRole.USER, true);
		User admin = insertUser(SystemRole.ADMIN, true);
		Project project = insertProject(owner);
		addMember(project, assignee);
		addMember(project, member);
		Task task = insertTask(project, owner, assignee);
		when(aiClient.generateSuggestion(anyString(), anyString())).thenReturn("Suggestion");

		suggest(login(member), task, null)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));
		suggest(login(admin), task, null)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));

		project.setArchivedAt(LocalDateTime.now());
		projectMapper.updateById(project);
		suggest(login(owner), task, null)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("PROJECT_ARCHIVED"));
	}

	@Test
	void ownerGeneratesValidatedTransientTaskPlan() throws Exception {
		User owner = insertUser(SystemRole.USER, true);
		User assignee = insertUser(SystemRole.USER, true);
		Project project = insertProject(owner);
		addMember(project, assignee);
		when(aiClient.responseFormatMode()).thenReturn(AiProperties.ResponseFormat.NONE);
		when(aiClient.generatePlan(anyString(), anyString())).thenReturn("""
			```json
			{"overview":"Plan","items":[{"draftId":"one","parentDraftId":null,
			"title":"First","description":null,"priority":"HIGH","dueDate":"2026-07-20",
			"assigneeId":"%s"}]}
			```
			""".formatted(assignee.getId()));

		generatePlan(login(owner), project, new PlanBody(
			"Build the project", null, new ConstraintsBody(2, LocalDate.of(2026, 8, 1))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.overview").value("Plan"))
			.andExpect(jsonPath("$.items[0].draftId").value("one"))
			.andExpect(jsonPath("$.items[0].assigneeId").value(assignee.getId().toString()))
			.andExpect(jsonPath("$.generatedAt").isString());

		ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
		verify(aiClient).generatePlan(anyString(), prompt.capture());
		assertThat(prompt.getValue())
			.doesNotContain(
				owner.getUsername(), owner.getEmail(), owner.getPhone(),
				assignee.getUsername(), assignee.getEmail(), assignee.getPhone());
		assertNoTasks(project);
	}

	@Test
	void taskPlanRejectsInvalidProviderOutputAndUnauthorizedCallers() throws Exception {
		User owner = insertUser(SystemRole.USER, true);
		User member = insertUser(SystemRole.USER, true);
		User admin = insertUser(SystemRole.ADMIN, true);
		Project project = insertProject(owner);
		addMember(project, member);
		when(aiClient.responseFormatMode()).thenReturn(AiProperties.ResponseFormat.NONE);
		when(aiClient.generatePlan(anyString(), anyString())).thenReturn(
			"{\"overview\":\"Bad\",\"items\":[]}");
		PlanBody body = new PlanBody("Build", null, null);

		generatePlan(login(owner), project, body)
			.andExpect(status().isBadGateway())
			.andExpect(jsonPath("$.code").value("AI_PROVIDER_ERROR"));
		generatePlan(login(member), project, body)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));
		generatePlan(login(admin), project, body)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));
		project.setArchivedAt(LocalDateTime.now());
		projectMapper.updateById(project);
		generatePlan(login(owner), project, body)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("PROJECT_ARCHIVED"));
		assertNoTasks(project);
	}

	@Test
	void providerCallRunsWithoutDatabaseTransactionOrProjectLock() throws Exception {
		User owner = insertUser(SystemRole.USER, true);
		Project project = insertProject(owner);
		CountDownLatch providerEntered = new CountDownLatch(1);
		CountDownLatch releaseProvider = new CountDownLatch(1);
		AtomicBoolean transactionActive = new AtomicBoolean(true);
		when(aiClient.responseFormatMode()).thenReturn(AiProperties.ResponseFormat.NONE);
		when(aiClient.generatePlan(anyString(), anyString())).thenAnswer(invocation -> {
			transactionActive.set(TransactionSynchronizationManager.isActualTransactionActive());
			providerEntered.countDown();
			assertThat(releaseProvider.await(5, TimeUnit.SECONDS)).isTrue();
			return "{\"overview\":\"Plan\",\"items\":[{\"draftId\":\"one\","
				+ "\"parentDraftId\":null,\"title\":\"One\",\"description\":null,"
				+ "\"priority\":\"MEDIUM\",\"dueDate\":null,\"assigneeId\":null}]}";
		});
		ExecutorService executor = Executors.newSingleThreadExecutor();
		try {
			Future<AiTaskPlanResponse> generation = executor.submit(() -> generationService.generatePlan(
				UsernamePasswordAuthenticationToken.authenticated(owner.getUsername(), "", List.of()),
				project.getId(),
				new AiTaskPlanGenerateRequest("Build", null, null)));
			assertThat(providerEntered.await(5, TimeUnit.SECONDS)).isTrue();
			project.setName("Updated While AI Waited");
			assertThat(projectMapper.updateById(project)).isEqualTo(1);
			releaseProvider.countDown();
			assertThat(generation.get(5, TimeUnit.SECONDS).overview()).isEqualTo("Plan");
			assertThat(transactionActive).isFalse();
		} finally {
			releaseProvider.countDown();
			executor.shutdownNow();
			assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
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

		importPlan(ownerSession, project, new ImportBody(List.of(
			item("x".repeat(101), null, "Long draft", null, Priority.MEDIUM, null, null))))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.errors[0].field").value("items[0].draftId"));
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

	private ResultActions suggest(LoginSession session, Task task, String focus) throws Exception {
		return mockMvc.perform(post("/api/ai/task-suggestions")
			.session(session.session())
			.header(session.headerName(), session.token())
			.contentType(MediaType.APPLICATION_JSON)
			.content(objectMapper.writeValueAsBytes(
				new SuggestionBody(task.getId().toString(), focus))));
	}

	private ResultActions generatePlan(LoginSession session, Project project, PlanBody body)
		throws Exception {
		return mockMvc.perform(post("/api/projects/{projectId}/ai/task-plans", project.getId())
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
		String marker = UUID.randomUUID().toString();
		User user = new User();
		user.setUsername("ai-import-" + marker);
		user.setPasswordHash(passwordEncoder.encode("test-password"));
		user.setDisplayName("AI Import User");
		user.setEmail(marker + "@example.com");
		user.setPhone("p" + marker.replace("-", "").substring(0, 19));
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

	private Task insertTask(Project project, User creator, User assignee) {
		Task task = new Task();
		task.setProjectId(project.getId());
		task.setCreatorId(creator.getId());
		task.setAssigneeId(assignee.getId());
		task.setTitle("AI Suggestion Task");
		task.setDescription("Only approved task fields may leave the service.");
		task.setStatus(TaskStatus.IN_PROGRESS);
		task.setPriority(Priority.MEDIUM);
		task.setDueDate(LocalDate.of(2026, 7, 20));
		taskMapper.insert(task);
		return task;
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

	record SuggestionBody(String taskId, String focus) {
	}

	record PlanBody(String goal, String description, ConstraintsBody constraints) {
	}

	record ConstraintsBody(Integer maxItems, LocalDate targetEndDate) {
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
