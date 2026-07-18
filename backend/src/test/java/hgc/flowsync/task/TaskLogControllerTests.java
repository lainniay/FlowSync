package hgc.flowsync.task;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import hgc.flowsync.user.SystemRole;
import hgc.flowsync.user.User;
import hgc.flowsync.user.UserMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TaskLogControllerTests {

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private ObjectMapper objectMapper;
	@MockitoSpyBean
	private UserMapper userMapper;
	@Autowired
	private ProjectMapper projectMapper;
	@Autowired
	private ProjectMemberMapper projectMemberMapper;
	@Autowired
	private TaskMapper taskMapper;
	@MockitoSpyBean
	private TaskLogMapper taskLogMapper;
	@Autowired
	private SummaryMapper summaryMapper;
	@Autowired
	private TaskLogService taskLogService;
	@Autowired
	private PasswordEncoder passwordEncoder;
	@Autowired
	private PlatformTransactionManager transactionManager;

	private final List<Long> projectIds = new ArrayList<>();
	private final List<Long> userIds = new ArrayList<>();

	@AfterEach
	void deleteCreatedRecords() {
		Mockito.reset(taskLogMapper, userMapper);
		if (!projectIds.isEmpty()) {
			List<Long> taskIds = taskMapper.selectList(Wrappers.<Task>lambdaQuery()
				.select(Task::getId)
				.in(Task::getProjectId, projectIds)).stream()
				.map(Task::getId)
				.toList();
			if (!taskIds.isEmpty()) {
				summaryMapper.delete(Wrappers.<Summary>lambdaQuery().in(Summary::getTaskId, taskIds));
				taskLogMapper.delete(Wrappers.<TaskLog>lambdaQuery().in(TaskLog::getTaskId, taskIds));
				taskMapper.update(null, Wrappers.<Task>lambdaUpdate()
					.in(Task::getId, taskIds)
					.set(Task::getParentId, null));
				taskMapper.delete(Wrappers.<Task>lambdaQuery().in(Task::getId, taskIds));
			}
			projectMemberMapper.delete(Wrappers.<ProjectMember>lambdaQuery()
				.in(ProjectMember::getProjectId, projectIds));
			projectMapper.delete(Wrappers.<Project>lambdaQuery().in(Project::getId, projectIds));
		}
		if (!userIds.isEmpty()) {
			userMapper.delete(Wrappers.<User>lambdaQuery().in(User::getId, userIds));
		}
	}

	@Test
	void memberAndAdminReadLogsWithFrozenResponseAndOneBatchOperatorQuery() throws Exception {
		User owner = insertUser(SystemRole.USER, "Owner");
		User member = insertUser(SystemRole.USER, "Member");
		User admin = insertUser(SystemRole.ADMIN, "Admin");
		Project project = insertProject(owner, ProjectStatus.IN_PROGRESS);
		addMember(project, member);
		Task task = insertTask(project, owner, member);
		TaskLog first = insertLog(task, owner, 20, "Owner progress");
		TaskLog latest = insertLog(task, member, 70, "Member progress");
		setCreatedAt(first, LocalDateTime.of(2026, 7, 17, 10, 0));
		setCreatedAt(latest, LocalDateTime.of(2026, 7, 17, 11, 0));
		LoginSession memberSession = login(member);
		Mockito.clearInvocations(userMapper);

		logRequest(get("/api/tasks/" + task.getId() + "/logs"), memberSession, null)
			.andExpect(status().isOk())
			.andExpect(content().contentType(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.page").value(0))
			.andExpect(jsonPath("$.size").value(20))
			.andExpect(jsonPath("$.totalElements").value(2))
			.andExpect(jsonPath("$.totalPages").value(1))
			.andExpect(jsonPath("$.items[0].id").value(latest.getId().toString()))
			.andExpect(jsonPath("$.items[0].taskId").value(task.getId().toString()))
			.andExpect(jsonPath("$.items[0].operator.id").value(member.getId().toString()))
			.andExpect(jsonPath("$.items[0].operator.displayName").value("Member"))
			.andExpect(jsonPath("$.items[0].progressPercent").value(70))
			.andExpect(jsonPath("$.items[0].content").value("Member progress"))
			.andExpect(jsonPath("$.items[0].createdAt")
				.value(ApiDateTime.toInstant(LocalDateTime.of(2026, 7, 17, 11, 0)).toString()))
			.andExpect(jsonPath("$.items[0].operatorId").doesNotExist());
		verify(userMapper, times(1)).selectBatchIds(anyCollection());

		logRequest(get("/api/tasks/" + task.getId() + "/logs"), login(admin), null)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(2));
		project.setArchivedAt(LocalDateTime.now());
		projectMapper.updateById(project);
		logRequest(get("/api/tasks/" + task.getId() + "/logs"), memberSession, null)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(2));
	}

	@Test
	void readHidesTasksFromOutsidersAndRemovedMembers() throws Exception {
		User owner = insertUser(SystemRole.USER, "Owner");
		User member = insertUser(SystemRole.USER, "Member");
		User outsider = insertUser(SystemRole.USER, "Outsider");
		Project project = insertProject(owner, ProjectStatus.IN_PROGRESS);
		addMember(project, member);
		Task task = insertTask(project, owner, member);
		insertLog(task, member, 30, "Visible while a member");
		LoginSession memberSession = login(member);

		mockMvc.perform(get("/api/tasks/" + task.getId() + "/logs"))
			.andExpect(status().isUnauthorized());
		assertProblem(logRequest(get("/api/tasks/" + task.getId() + "/logs"), login(outsider), null),
			404, "NOT_FOUND");
		projectMemberMapper.delete(Wrappers.<ProjectMember>lambdaQuery()
			.eq(ProjectMember::getProjectId, project.getId())
			.eq(ProjectMember::getUserId, member.getId()));
		assertProblem(logRequest(get("/api/tasks/" + task.getId() + "/logs"), memberSession, null),
			404, "NOT_FOUND");
		assertProblem(logRequest(get("/api/tasks/" + Long.MAX_VALUE + "/logs"), login(owner), null),
			404, "NOT_FOUND");
		assertProblem(logRequest(get("/api/tasks/not-a-number/logs"), login(owner), null),
			422, "VALIDATION_ERROR");
	}

	@Test
	void listPaginationSortingAndValidationMatchTheContract() throws Exception {
		User owner = insertUser(SystemRole.USER, "Owner");
		Project project = insertProject(owner, ProjectStatus.IN_PROGRESS);
		Task task = insertTask(project, owner, owner);
		TaskLog first = insertLog(task, owner, 30, "First");
		TaskLog second = insertLog(task, owner, 10, "Second");
		TaskLog third = insertLog(task, owner, 20, "Third");
		TaskLog fourth = insertLog(task, owner, 40, "Fourth");
		setCreatedAt(first, LocalDateTime.of(2026, 7, 17, 10, 0));
		setCreatedAt(second, LocalDateTime.of(2026, 7, 17, 10, 0));
		setCreatedAt(third, LocalDateTime.of(2026, 7, 17, 11, 0));
		setCreatedAt(fourth, LocalDateTime.of(2026, 7, 17, 12, 0));
		LoginSession session = login(owner);

		assertThat(logIds(session, task, "createdAt,desc", 0, 20))
			.containsExactly(fourth.getId().toString(), third.getId().toString(),
				first.getId().toString(), second.getId().toString());
		assertThat(logIds(session, task, "createdAt,asc", 0, 20))
			.containsExactly(first.getId().toString(), second.getId().toString(),
				third.getId().toString(), fourth.getId().toString());
		assertThat(logIds(session, task, "progressPercent,asc", 0, 20))
			.containsExactly(second.getId().toString(), third.getId().toString(),
				first.getId().toString(), fourth.getId().toString());
		assertThat(logIds(session, task, "progressPercent,desc", 0, 20))
			.containsExactly(fourth.getId().toString(), first.getId().toString(),
				third.getId().toString(), second.getId().toString());
		assertThat(logIds(session, task, "createdAt,desc", 0, 2))
			.containsExactly(fourth.getId().toString(), third.getId().toString());
		assertThat(logIds(session, task, "createdAt,desc", 1, 2))
			.containsExactly(first.getId().toString(), second.getId().toString());
		assertThat(logIds(session, task, "createdAt,desc", 9, 2)).isEmpty();

		for (String query : List.of(
			"page=", "size=", "sort=", "page=%20", "size=%20", "sort=%20",
			"page=one", "size=two", "page=-1", "size=0", "size=101",
			"sort=id,asc", "sort=createdAt,sideways", "sort=createdAt",
			"sort=createdAt,desc,extra")) {
			assertProblem(logRequest(get("/api/tasks/" + task.getId() + "/logs?" + query), session, null),
				422, "VALIDATION_ERROR");
		}
	}

	@Test
	void ownerAndAssigneeCreateLogsFromSessionAndUpdateTaskProgress() throws Exception {
		User owner = insertUser(SystemRole.USER, "Owner");
		User assignee = insertUser(SystemRole.USER, "Assignee");
		Project project = insertProject(owner, ProjectStatus.IN_PROGRESS);
		addMember(project, assignee);
		Task task = insertTask(project, owner, assignee);
		Map<String, Object> spoofed = new LinkedHashMap<>();
		spoofed.put("progressPercent", 0);
		spoofed.put("content", "Owner started work");
		spoofed.put("operatorId", assignee.getId().toString());
		spoofed.put("operator", Map.of("id", assignee.getId().toString()));
		spoofed.put("createdAt", "2000-01-01T00:00:00Z");

		MvcResult createdResult = logRequest(post("/api/tasks/" + task.getId() + "/logs"),
			login(owner), spoofed)
			.andExpect(status().isCreated())
			.andExpect(content().contentType(MediaType.APPLICATION_JSON))
			.andReturn();
		JsonNode created = objectMapper.readTree(createdResult.getResponse().getContentAsByteArray());
		TaskLog saved = taskLogMapper.selectById(created.get("id").asText());
		assertThat(created.get("id").isTextual()).isTrue();
		assertThat(created.get("id").asText()).isEqualTo(saved.getId().toString());
		assertThat(created.get("taskId").asText()).isEqualTo(task.getId().toString());
		assertThat(created.at("/operator/id").asText()).isEqualTo(owner.getId().toString());
		assertThat(created.at("/operator/displayName").asText()).isEqualTo("Owner");
		assertThat(created.get("progressPercent").asInt()).isZero();
		assertThat(created.get("content").asText()).isEqualTo("Owner started work");
		assertThat(created.get("createdAt").asText())
			.isEqualTo(ApiDateTime.toInstant(saved.getCreatedAt()).toString());
		assertThat(created.has("operatorId")).isFalse();
		assertThat(saved.getOperatorId()).isEqualTo(owner.getId());

		logRequest(post("/api/tasks/" + task.getId() + "/logs"), login(assignee),
			Map.of("progressPercent", 100, "content", "Assignee completed work"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.operator.id").value(assignee.getId().toString()))
			.andExpect(jsonPath("$.progressPercent").value(100));
		logRequest(get("/api/tasks/" + task.getId()), login(owner), null)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.progressPercent").value(100));
		logRequest(get("/api/tasks?projectId=" + project.getId()), login(owner), null)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].progressPercent").value(100));
	}

	@Test
	void everyUnarchivedProjectStatusAndContentLengthBoundaryAllowCreate() throws Exception {
		User owner = insertUser(SystemRole.USER, "Owner");
		for (ProjectStatus status : ProjectStatus.values()) {
			Project project = insertProject(owner, status);
			Task task = insertTask(project, owner, owner);
			logRequest(post("/api/tasks/" + task.getId() + "/logs"), login(owner),
				Map.of("progressPercent", 50, "content", "x".repeat(1000)))
				.andExpect(status().isCreated());
		}
	}

	@Test
	void createPermissionsAndArchiveChecksUseCurrentLockedState() throws Exception {
		User owner = insertUser(SystemRole.USER, "Owner");
		User assignee = insertUser(SystemRole.USER, "Assignee");
		User member = insertUser(SystemRole.USER, "Member");
		User outsider = insertUser(SystemRole.USER, "Outsider");
		User admin = insertUser(SystemRole.ADMIN, "Admin");
		Project project = insertProject(owner, ProjectStatus.IN_PROGRESS);
		addMember(project, assignee);
		addMember(project, member);
		Task task = insertTask(project, owner, assignee);
		Map<String, Object> body = Map.of("progressPercent", 25, "content", "Attempt");

		assertProblem(logRequest(post("/api/tasks/" + task.getId() + "/logs"), login(member), body),
			403, "FORBIDDEN");
		assertProblem(logRequest(post("/api/tasks/" + task.getId() + "/logs"), login(admin), body),
			403, "FORBIDDEN");
		assertProblem(logRequest(post("/api/tasks/" + task.getId() + "/logs"), login(outsider), body),
			404, "NOT_FOUND");
		LoginSession assigneeSession = login(assignee);
		projectMemberMapper.delete(Wrappers.<ProjectMember>lambdaQuery()
			.eq(ProjectMember::getProjectId, project.getId())
			.eq(ProjectMember::getUserId, assignee.getId()));
		assertProblem(logRequest(post("/api/tasks/" + task.getId() + "/logs"), assigneeSession, body),
			404, "NOT_FOUND");
		assertProblem(logRequest(post("/api/tasks/" + Long.MAX_VALUE + "/logs"), login(owner), body),
			404, "NOT_FOUND");

		project.setArchivedAt(LocalDateTime.now());
		projectMapper.updateById(project);
		long before = taskLogMapper.selectCount(Wrappers.<TaskLog>lambdaQuery()
			.eq(TaskLog::getTaskId, task.getId()));
		assertProblem(logRequest(post("/api/tasks/" + task.getId() + "/logs"), login(owner), body),
			409, "PROJECT_ARCHIVED");
		assertThat(taskLogMapper.selectCount(Wrappers.<TaskLog>lambdaQuery()
			.eq(TaskLog::getTaskId, task.getId()))).isEqualTo(before);
	}

	@Test
	void createValidationAuthenticationAndCsrfMatchExistingProblemDetails() throws Exception {
		User owner = insertUser(SystemRole.USER, "Owner");
		Project project = insertProject(owner, ProjectStatus.IN_PROGRESS);
		Task task = insertTask(project, owner, owner);
		LoginSession session = login(owner);
		String path = "/api/tasks/" + task.getId() + "/logs";

		for (String body : List.of(
			"{\"content\":\"missing progress\"}",
			"{\"progressPercent\":null,\"content\":\"null progress\"}",
			"{\"progressPercent\":-1,\"content\":\"low\"}",
			"{\"progressPercent\":101,\"content\":\"high\"}",
			"{\"progressPercent\":1.5,\"content\":\"fraction\"}",
			"{\"progressPercent\":\"1\",\"content\":\"string\"}",
			"{\"progressPercent\":true,\"content\":\"boolean\"}",
			"{\"progressPercent\":{},\"content\":\"object\"}",
			"{\"progressPercent\":[],\"content\":\"array\"}",
			"{\"progressPercent\":20}",
			"{\"progressPercent\":20,\"content\":null}",
			"{\"progressPercent\":20,\"content\":\"\"}",
			"{\"progressPercent\":20,\"content\":\"   \"}",
			"{\"progressPercent\":20,\"content\":\"" + "x".repeat(1001) + "\"}")) {
			assertProblem(logRequest(post(path), session, body), 422, "VALIDATION_ERROR");
		}
		assertProblem(logRequest(post(path), session, "[]"), 400, "BAD_REQUEST");
		assertProblem(logRequest(post(path), session, "\"not an object\""), 400, "BAD_REQUEST");

		LoginSession anonymous = csrfSession();
		mockMvc.perform(post(path)
				.session(anonymous.session())
				.header(anonymous.headerName(), anonymous.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"progressPercent\":20,\"content\":\"anonymous\"}"))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(post(path)
				.session(session.session())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"progressPercent\":20,\"content\":\"missing csrf\"}"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("CSRF_INVALID"));
		mockMvc.perform(post(path)
				.session(session.session())
				.header(session.headerName(), "wrong-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"progressPercent\":20,\"content\":\"wrong csrf\"}"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("CSRF_INVALID"));
		assertThat(taskLogMapper.selectCount(Wrappers.<TaskLog>lambdaQuery()
			.eq(TaskLog::getTaskId, task.getId()))).isZero();
	}

	@Test
	void ownerAndOperatorDeleteWhileOtherIdentitiesReceiveHiddenOrForbiddenErrors() throws Exception {
		User owner = insertUser(SystemRole.USER, "Owner");
		User operator = insertUser(SystemRole.USER, "Operator");
		User member = insertUser(SystemRole.USER, "Member");
		User outsider = insertUser(SystemRole.USER, "Outsider");
		User admin = insertUser(SystemRole.ADMIN, "Admin");
		Project project = insertProject(owner, ProjectStatus.IN_PROGRESS);
		addMember(project, operator);
		addMember(project, member);
		Task task = insertTask(project, owner, operator);
		Task otherTask = insertTask(project, owner, operator);
		TaskLog ownerDeletes = insertLog(task, operator, 10, "Owner deletes");
		TaskLog operatorDeletes = insertLog(task, operator, 20, "Operator deletes");

		logRequest(delete("/api/tasks/" + task.getId() + "/logs/" + ownerDeletes.getId()),
			login(owner), null)
			.andExpect(status().isNoContent())
			.andExpect(content().string(""));
		assertThat(taskLogMapper.selectById(ownerDeletes.getId())).isNull();
		logRequest(delete("/api/tasks/" + task.getId() + "/logs/" + operatorDeletes.getId()),
			login(operator), null)
			.andExpect(status().isNoContent())
			.andExpect(content().string(""));

		TaskLog protectedLog = insertLog(task, operator, 30, "Protected");
		assertProblem(logRequest(delete("/api/tasks/" + task.getId() + "/logs/" + protectedLog.getId()),
			login(member), null), 403, "FORBIDDEN");
		assertProblem(logRequest(delete("/api/tasks/" + task.getId() + "/logs/" + protectedLog.getId()),
			login(admin), null), 403, "FORBIDDEN");
		assertProblem(logRequest(delete("/api/tasks/" + task.getId() + "/logs/" + protectedLog.getId()),
			login(outsider), null), 404, "NOT_FOUND");
		assertProblem(logRequest(delete("/api/tasks/" + otherTask.getId() + "/logs/" + protectedLog.getId()),
			login(owner), null), 404, "NOT_FOUND");
		assertProblem(logRequest(delete("/api/tasks/" + task.getId() + "/logs/" + Long.MAX_VALUE),
			login(owner), null), 404, "NOT_FOUND");
		assertProblem(logRequest(delete("/api/tasks/" + Long.MAX_VALUE + "/logs/" + protectedLog.getId()),
			login(owner), null), 404, "NOT_FOUND");
		assertProblem(logRequest(delete("/api/tasks/" + task.getId() + "/logs/not-a-number"),
			login(owner), null), 422, "VALIDATION_ERROR");
		assertThat(taskLogMapper.selectById(protectedLog.getId())).isNotNull();

		LoginSession operatorSession = login(operator);
		projectMemberMapper.delete(Wrappers.<ProjectMember>lambdaQuery()
			.eq(ProjectMember::getProjectId, project.getId())
			.eq(ProjectMember::getUserId, operator.getId()));
		assertProblem(logRequest(delete("/api/tasks/" + task.getId() + "/logs/" + protectedLog.getId()),
			operatorSession, null), 404, "NOT_FOUND");
	}

	@Test
	void deleteHonorsArchiveAuthenticationAndCsrf() throws Exception {
		User owner = insertUser(SystemRole.USER, "Owner");
		Project project = insertProject(owner, ProjectStatus.IN_PROGRESS);
		Task task = insertTask(project, owner, owner);
		TaskLog taskLog = insertLog(task, owner, 50, "Archived log");
		LoginSession session = login(owner);
		String path = "/api/tasks/" + task.getId() + "/logs/" + taskLog.getId();

		LoginSession anonymous = csrfSession();
		mockMvc.perform(delete(path)
				.session(anonymous.session())
				.header(anonymous.headerName(), anonymous.token()))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(delete(path).session(session.session()))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("CSRF_INVALID"));
		mockMvc.perform(delete(path)
				.session(session.session())
				.header(session.headerName(), "wrong-token"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("CSRF_INVALID"));

		project.setArchivedAt(LocalDateTime.now());
		projectMapper.updateById(project);
		assertProblem(logRequest(delete(path), session, null), 409, "PROJECT_ARCHIVED");
		assertThat(taskLogMapper.selectById(taskLog.getId())).isNotNull();
	}

	@Test
	void deletingLogsRecomputesLatestProgressForTaskListAndDetail() throws Exception {
		User owner = insertUser(SystemRole.USER, "Owner");
		Project project = insertProject(owner, ProjectStatus.IN_PROGRESS);
		Task task = insertTask(project, owner, owner);
		TaskLog first = insertLog(task, owner, 20, "First");
		TaskLog second = insertLog(task, owner, 80, "Second");
		LocalDateTime tied = LocalDateTime.of(2026, 7, 17, 10, 0);
		setCreatedAt(first, tied);
		setCreatedAt(second, tied);
		LoginSession session = login(owner);

		assertTaskProgress(session, project, task, 80);
		logRequest(delete("/api/tasks/" + task.getId() + "/logs/" + first.getId()), session, null)
			.andExpect(status().isNoContent());
		assertTaskProgress(session, project, task, 80);
		TaskLog latest = insertLog(task, owner, 60, "Latest by time");
		setCreatedAt(latest, tied.plusHours(1));
		assertTaskProgress(session, project, task, 60);
		logRequest(delete("/api/tasks/" + task.getId() + "/logs/" + latest.getId()), session, null)
			.andExpect(status().isNoContent());
		assertTaskProgress(session, project, task, 80);
		logRequest(delete("/api/tasks/" + task.getId() + "/logs/" + second.getId()), session, null)
			.andExpect(status().isNoContent());
		assertTaskProgress(session, project, task, 0);
	}

	@Test
	void unknownDatabaseFailuresRemainInternalErrorsWithoutPartialWrites() throws Exception {
		User owner = insertUser(SystemRole.USER, "Owner");
		Project project = insertProject(owner, ProjectStatus.IN_PROGRESS);
		Task task = insertTask(project, owner, owner);
		LoginSession session = login(owner);
		String createPath = "/api/tasks/" + task.getId() + "/logs";
		doThrow(new DataIntegrityViolationException("unknown insert failure"))
			.when(taskLogMapper).insert(any(TaskLog.class));

		assertProblem(logRequest(post(createPath), session,
			Map.of("progressPercent", 40, "content", "Fails")), 500, "INTERNAL_SERVER_ERROR");
		assertThat(taskLogMapper.selectCount(Wrappers.<TaskLog>lambdaQuery()
			.eq(TaskLog::getTaskId, task.getId()))).isZero();
		Mockito.reset(taskLogMapper);

		TaskLog taskLog = insertLog(task, owner, 40, "Delete fails");
		doThrow(new DataIntegrityViolationException("unknown delete failure"))
			.when(taskLogMapper).deleteById(taskLog.getId());
		assertProblem(logRequest(delete(createPath + "/" + taskLog.getId()), session, null),
			500, "INTERNAL_SERVER_ERROR");
		assertThat(taskLogMapper.selectById(taskLog.getId())).isNotNull();
	}

	@Test
	void outerTransactionFailureRollsBackCompletedCreateAndDelete() {
		User owner = insertUser(SystemRole.USER, "Owner");
		Project project = insertProject(owner, ProjectStatus.IN_PROGRESS);
		Task task = insertTask(project, owner, owner);
		Authentication authentication = authentication(owner);
		TransactionTemplate transaction = new TransactionTemplate(transactionManager);

		assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
			taskLogService.create(authentication, task.getId(), 40, "Rollback create");
			throw new IllegalStateException("rollback create");
		})).isInstanceOf(IllegalStateException.class);
		assertThat(taskLogMapper.selectCount(Wrappers.<TaskLog>lambdaQuery()
			.eq(TaskLog::getTaskId, task.getId()))).isZero();

		TaskLog taskLog = insertLog(task, owner, 50, "Rollback delete");
		assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
			taskLogService.delete(authentication, task.getId(), taskLog.getId());
			throw new IllegalStateException("rollback delete");
		})).isInstanceOf(IllegalStateException.class);
		assertThat(taskLogMapper.selectById(taskLog.getId())).isNotNull();
	}

	private List<String> logIds(
		LoginSession session,
		Task task,
		String sort,
		int page,
		int size) throws Exception {
		MvcResult result = logRequest(get("/api/tasks/" + task.getId() + "/logs?sort=" + sort
			+ "&page=" + page + "&size=" + size), session, null)
			.andExpect(status().isOk())
			.andReturn();
		List<String> ids = new ArrayList<>();
		objectMapper.readTree(result.getResponse().getContentAsByteArray()).get("items")
			.forEach(item -> ids.add(item.get("id").asText()));
		return ids;
	}

	private void assertTaskProgress(LoginSession session, Project project, Task task, int progress)
		throws Exception {
		logRequest(get("/api/tasks/" + task.getId()), session, null)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.progressPercent").value(progress));
		logRequest(get("/api/tasks?projectId=" + project.getId()), session, null)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].progressPercent").value(progress));
	}

	private ResultActions assertProblem(ResultActions result, int statusCode, String code) throws Exception {
		return result
			.andExpect(status().is(statusCode))
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value(code));
	}

	private ResultActions logRequest(
		org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
		LoginSession session,
		Object body) throws Exception {
		request.session(session.session())
			.header(session.headerName(), session.token());
		if (body != null) {
			request.contentType(MediaType.APPLICATION_JSON)
				.content(body instanceof String value ? value : objectMapper.writeValueAsString(body));
		}
		return mockMvc.perform(request);
	}

	private LoginSession login(User user) throws Exception {
		LoginSession csrfSession = csrfSession();
		mockMvc.perform(post("/api/auth/login")
				.session(csrfSession.session())
				.header(csrfSession.headerName(), csrfSession.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsBytes(
					new LoginBody(user.getUsername(), "test-password"))))
			.andExpect(status().isOk());
		return csrfSession;
	}

	private LoginSession csrfSession() throws Exception {
		MvcResult csrfResult = mockMvc.perform(get("/api/auth/csrf")).andReturn();
		JsonNode csrf = objectMapper.readTree(csrfResult.getResponse().getContentAsByteArray());
		MockHttpSession session = (MockHttpSession) csrfResult.getRequest().getSession(false);
		return new LoginSession(session, csrf.get("headerName").asText(), csrf.get("token").asText());
	}

	private static Authentication authentication(User user) {
		return UsernamePasswordAuthenticationToken.authenticated(user.getUsername(), "", List.of());
	}

	private User insertUser(SystemRole role, String displayName) {
		User user = new User();
		user.setUsername("task-log-http-" + UUID.randomUUID());
		user.setPasswordHash(passwordEncoder.encode("test-password"));
		user.setDisplayName(displayName);
		user.setSystemRole(role);
		user.setActive(true);
		userMapper.insert(user);
		userIds.add(user.getId());
		return userMapper.selectById(user.getId());
	}

	private Project insertProject(User owner, ProjectStatus status) {
		Project project = new Project();
		project.setOwnerId(owner.getId());
		project.setName("TaskLog HTTP " + UUID.randomUUID());
		project.setStatus(status);
		project.setPriority(Priority.MEDIUM);
		projectMapper.insert(project);
		projectIds.add(project.getId());
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
		task.setCreatorId(creator.getId());
		task.setAssigneeId(assignee == null ? null : assignee.getId());
		task.setTitle("TaskLog task " + UUID.randomUUID());
		task.setStatus(TaskStatus.IN_PROGRESS);
		task.setPriority(Priority.MEDIUM);
		taskMapper.insert(task);
		return taskMapper.selectById(task.getId());
	}

	private TaskLog insertLog(Task task, User operator, int progressPercent, String value) {
		TaskLog taskLog = new TaskLog();
		taskLog.setTaskId(task.getId());
		taskLog.setOperatorId(operator.getId());
		taskLog.setProgressPercent(progressPercent);
		taskLog.setContent(value);
		taskLogMapper.insert(taskLog);
		return taskLogMapper.selectById(taskLog.getId());
	}

	private void setCreatedAt(TaskLog taskLog, LocalDateTime createdAt) {
		taskLogMapper.update(null, Wrappers.<TaskLog>lambdaUpdate()
			.eq(TaskLog::getId, taskLog.getId())
			.set(TaskLog::getCreatedAt, createdAt));
		taskLog.setCreatedAt(createdAt);
	}

	private record LoginBody(String username, String password) {
	}

	private record LoginSession(MockHttpSession session, String headerName, String token) {
	}

}
