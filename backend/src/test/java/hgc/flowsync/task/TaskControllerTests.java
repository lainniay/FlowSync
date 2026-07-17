package hgc.flowsync.task;

import java.time.LocalDate;
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
import hgc.flowsync.summary.SummaryType;
import hgc.flowsync.user.SystemRole;
import hgc.flowsync.user.User;
import hgc.flowsync.user.UserMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TaskControllerTests {

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
	@Autowired
	private SummaryMapper summaryMapper;
	@Autowired
	private PasswordEncoder passwordEncoder;

	private final List<Long> projectIds = new ArrayList<>();
	private final List<Long> userIds = new ArrayList<>();

	@AfterEach
	void deleteCreatedRecords() {
		Mockito.reset(taskMapper);
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
	void ownerCanUseAllSixTaskEndpoints() throws Exception {
		User owner = insertUser(SystemRole.USER, true, "Task Owner");
		Project project = insertProject(owner);
		LoginSession session = login(owner);

		MvcResult createdResult = taskRequest(post("/api/tasks"), session, body(project, null, owner))
			.andExpect(status().isCreated())
			.andExpect(content().contentType(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.owner").doesNotExist())
			.andReturn();
		JsonNode created = objectMapper.readTree(createdResult.getResponse().getContentAsByteArray());
		String taskId = created.get("id").asText();
		Task saved = taskMapper.selectById(taskId);
		assertThat(created.get("id").isTextual()).isTrue();
		assertThat(created.get("id").asText()).isEqualTo(saved.getId().toString());
		assertThat(created.get("projectId").asText()).isEqualTo(project.getId().toString());
		assertThat(created.get("parentId").isNull()).isTrue();
		assertThat(created.at("/assignee/id").asText()).isEqualTo(owner.getId().toString());
		assertThat(created.at("/assignee/displayName").asText()).isEqualTo(owner.getDisplayName());
		assertThat(created.at("/creator/id").asText()).isEqualTo(owner.getId().toString());
		assertThat(created.at("/creator/displayName").asText()).isEqualTo(owner.getDisplayName());
		assertThat(created.get("title").asText()).isEqualTo("HTTP Task");
		assertThat(created.get("description").asText()).isEqualTo("HTTP task description");
		assertThat(created.get("status").asText()).isEqualTo("NOT_STARTED");
		assertThat(created.get("priority").asText()).isEqualTo("HIGH");
		assertThat(created.get("progressPercent").asInt()).isZero();
		assertThat(created.get("dueDate").asText()).isEqualTo("2026-07-15");
		assertThat(created.get("createdAt").asText())
			.isEqualTo(ApiDateTime.toInstant(saved.getCreatedAt()).toString());
		assertThat(created.get("updatedAt").asText())
			.isEqualTo(ApiDateTime.toInstant(saved.getUpdatedAt()).toString());

		taskRequest(get("/api/tasks?projectId=" + project.getId()), session, null)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].id").value(taskId));
		taskRequest(get("/api/tasks/" + taskId), session, null)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.title").value("HTTP Task"));

		Map<String, Object> replacement = new LinkedHashMap<>(updateBody(project, null, null));
		replacement.put("title", "Replaced Task");
		replacement.put("description", null);
		replacement.put("dueDate", null);
		replacement.put("status", TaskStatus.IN_PROGRESS);
		replacement.put("priority", Priority.LOW);
		taskRequest(put("/api/tasks/" + taskId), session, replacement)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.title").value("Replaced Task"))
			.andExpect(jsonPath("$.description").value((Object) null))
			.andExpect(jsonPath("$.assignee").value((Object) null))
			.andExpect(jsonPath("$.dueDate").value((Object) null));
		taskRequest(put("/api/tasks/" + taskId + "/status"), session,
			new StatusBody(TaskStatus.COMPLETED))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("COMPLETED"))
			.andExpect(jsonPath("$.title").value("Replaced Task"));
		taskRequest(delete("/api/tasks/" + taskId), session, null)
			.andExpect(status().isNoContent());
		assertThat(taskMapper.selectById(taskId)).isNull();
	}

	@Test
	void presentButEmptyOrBlankListParametersAreValidationErrors() throws Exception {
		User owner = insertUser(SystemRole.USER, true, "Owner");
		LoginSession session = login(owner);

		for (String query : List.of(
			"status=", "priority=", "page=", "size=", "sort=",
			"status=%20", "priority=%20", "page=%20", "size=%20", "sort=%20",
			"dueBefore=", "dueAfter=", "q=")) {
			taskRequest(get("/api/tasks?" + query), session, null)
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		}
	}

	@Test
	void authenticationVisibilityAndWritePermissionsMatchTheMatrix() throws Exception {
		User owner = insertUser(SystemRole.USER, true, "Owner");
		User member = insertUser(SystemRole.USER, true, "Member");
		User assignee = insertUser(SystemRole.USER, true, "Assignee");
		User outsider = insertUser(SystemRole.USER, true, "Outsider");
		User admin = insertUser(SystemRole.ADMIN, true, "Admin");
		Project project = insertProject(owner);
		addMember(project, member);
		addMember(project, assignee);
		Task task = insertTask(project, owner, assignee, null, "Protected", TaskStatus.NOT_STARTED);

		mockMvc.perform(get("/api/tasks/" + task.getId()))
			.andExpect(status().isUnauthorized());
		taskRequest(get("/api/tasks/" + task.getId()), login(member), null)
			.andExpect(status().isOk());
		taskRequest(get("/api/tasks/" + task.getId()), login(admin), null)
			.andExpect(status().isOk());
		taskRequest(get("/api/tasks/" + task.getId()), login(outsider), null)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOT_FOUND"));

		Map<String, Object> replacement = updateBody(project, null, assignee);
		taskRequest(put("/api/tasks/" + task.getId()), login(member), replacement)
			.andExpect(status().isForbidden());
		taskRequest(delete("/api/tasks/" + task.getId()), login(member), null)
			.andExpect(status().isForbidden());
		taskRequest(put("/api/tasks/" + task.getId() + "/status"), login(member),
			new StatusBody(TaskStatus.COMPLETED))
			.andExpect(status().isForbidden());
		taskRequest(put("/api/tasks/" + task.getId() + "/status"), login(assignee),
			new StatusBody(TaskStatus.IN_PROGRESS))
			.andExpect(status().isOk());

		LoginSession adminSession = login(admin);
		taskRequest(post("/api/tasks"), adminSession, body(project, null, assignee))
			.andExpect(status().isForbidden());
		taskRequest(put("/api/tasks/" + task.getId()), adminSession, replacement)
			.andExpect(status().isForbidden());
		taskRequest(put("/api/tasks/" + task.getId() + "/status"), adminSession,
			new StatusBody(TaskStatus.COMPLETED))
			.andExpect(status().isForbidden());
		taskRequest(delete("/api/tasks/" + task.getId()), adminSession, null)
			.andExpect(status().isForbidden());

		projectMemberMapper.delete(Wrappers.<ProjectMember>lambdaQuery()
			.eq(ProjectMember::getProjectId, project.getId())
			.eq(ProjectMember::getUserId, assignee.getId()));
		taskRequest(put("/api/tasks/" + task.getId() + "/status"), login(assignee),
			new StatusBody(TaskStatus.COMPLETED))
			.andExpect(status().isNotFound());
		assertThat(taskMapper.selectById(task.getId()).getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
	}

	@Test
	void archivedProjectBlocksAuthorizedWritesAfterPermissionChecks() throws Exception {
		User owner = insertUser(SystemRole.USER, true, "Owner");
		User outsider = insertUser(SystemRole.USER, true, "Outsider");
		Project project = insertProject(owner);
		Task task = insertTask(project, owner, owner, null, "Archived", TaskStatus.NOT_STARTED);
		project.setArchivedAt(LocalDateTime.now());
		projectMapper.updateById(project);
		long before = taskMapper.selectCount(Wrappers.<Task>lambdaQuery()
			.eq(Task::getProjectId, project.getId()));

		expectProblem(taskRequest(post("/api/tasks"), login(owner), body(project, null, owner)),
			409, "PROJECT_ARCHIVED", "/api/tasks")
			.andExpect(jsonPath("$.errors").isEmpty());
		expectProblem(taskRequest(put("/api/tasks/" + task.getId() + "/status"), login(owner),
			new StatusBody(TaskStatus.IN_PROGRESS)),
			409, "PROJECT_ARCHIVED", "/api/tasks/" + task.getId() + "/status")
			.andExpect(jsonPath("$.errors").isEmpty());
		assertThat(taskMapper.selectCount(Wrappers.<Task>lambdaQuery()
			.eq(Task::getProjectId, project.getId()))).isEqualTo(before);
		assertThat(taskMapper.selectById(task.getId()).getStatus()).isEqualTo(TaskStatus.NOT_STARTED);

		expectProblem(taskRequest(put("/api/tasks/" + task.getId()), login(owner), updateBody(project, null, owner)),
			409, "PROJECT_ARCHIVED", "/api/tasks/" + task.getId())
			.andExpect(jsonPath("$.errors").isEmpty());
		taskRequest(delete("/api/tasks/" + task.getId()), login(owner), null)
			.andExpect(status().isConflict());
		taskRequest(put("/api/tasks/" + task.getId()), login(outsider), updateBody(project, null, owner))
			.andExpect(status().isNotFound());
	}

	@Test
	void assigneeAndDueDateRulesAreEnforcedWithoutPartialWrites() throws Exception {
		User owner = insertUser(SystemRole.USER, true, "Owner");
		User member = insertUser(SystemRole.USER, true, "Member");
		User outsider = insertUser(SystemRole.USER, true, "Outsider");
		User inactive = insertUser(SystemRole.USER, false, "Inactive");
		User admin = insertUser(SystemRole.ADMIN, true, "Admin");
		Project project = insertProject(owner);
		addMember(project, member);
		addMember(project, inactive);
		addMember(project, admin);
		LoginSession ownerSession = login(owner);

		taskRequest(post("/api/tasks"), ownerSession, body(project, null, null))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.assignee").value((Object) null));
		taskRequest(post("/api/tasks"), ownerSession, body(project, null, member))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.assignee.id").value(member.getId().toString()));

		assertCreateError(ownerSession, body(project, null, outsider), "VALIDATION_ERROR", 422);
		assertCreateError(ownerSession, body(project, null, inactive), "VALIDATION_ERROR", 422);
		assertCreateError(ownerSession, body(project, null, admin), "VALIDATION_ERROR", 422);
		TaskBody missing = body(project, null, member).withAssignee(Long.toString(Long.MAX_VALUE));
		assertCreateError(ownerSession, missing, "NOT_FOUND", 404);

		TaskBody start = body(project, null, member).withDueDate(project.getStartDate());
		TaskBody end = body(project, null, member).withDueDate(project.getEndDate());
		taskRequest(post("/api/tasks"), ownerSession, start).andExpect(status().isCreated());
		taskRequest(post("/api/tasks"), ownerSession, end).andExpect(status().isCreated());
		assertCreateError(ownerSession,
			body(project, null, member).withDueDate(project.getStartDate().minusDays(1)),
			"VALIDATION_ERROR", 422);
		assertCreateError(ownerSession,
			body(project, null, member).withDueDate(project.getEndDate().plusDays(1)),
			"VALIDATION_ERROR", 422);
	}

	@Test
	void parentMustBeInTheProjectAndMustNotCreateAnyDepthCycle() throws Exception {
		User owner = insertUser(SystemRole.USER, true, "Owner");
		Project project = insertProject(owner);
		Project otherProject = insertProject(owner);
		Task root = insertTask(project, owner, owner, null, "Root", TaskStatus.NOT_STARTED);
		Task child = insertTask(project, owner, owner, root, "Child", TaskStatus.NOT_STARTED);
		Task grandchild = insertTask(project, owner, owner, child, "Grandchild", TaskStatus.NOT_STARTED);
		Task foreign = insertTask(otherProject, owner, owner, null, "Foreign", TaskStatus.NOT_STARTED);
		LoginSession session = login(owner);

		taskRequest(post("/api/tasks"), session, body(project, root, owner))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.parentId").value(root.getId().toString()));
		taskRequest(post("/api/tasks"), session, body(project, foreign, owner))
			.andExpect(status().isNotFound());
		taskRequest(put("/api/tasks/" + root.getId()), session, updateBody(project, root, owner))
			.andExpect(status().isUnprocessableEntity());
		taskRequest(put("/api/tasks/" + root.getId()), session, updateBody(project, child, owner))
			.andExpect(status().isUnprocessableEntity());
		taskRequest(put("/api/tasks/" + root.getId()), session, updateBody(project, grandchild, owner))
			.andExpect(status().isUnprocessableEntity());
		assertThat(taskMapper.selectById(root.getId()).getParentId()).isNull();
	}

	@Test
	void fullPutRequiresEveryFieldAndFailedValidationLeavesTaskUnchanged() throws Exception {
		User owner = insertUser(SystemRole.USER, true, "Owner");
		Project project = insertProject(owner);
		Task task = insertTask(project, owner, owner, null, "Original", TaskStatus.NOT_STARTED);
		LoginSession session = login(owner);

		taskRequest(put("/api/tasks/" + task.getId()), session, """
			{"parentId":null,"title":"Missing Description","assigneeId":null,
			 "status":"IN_PROGRESS","priority":"HIGH","dueDate":null}
			""")
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
			.andExpect(jsonPath("$.errors[0].field").value("description"));
		taskRequest(put("/api/tasks/" + task.getId()), session, """
			{"parentId":null,"title":null,"description":null,"assigneeId":null,
			 "status":"NOT_A_STATUS","priority":"HIGH","dueDate":null}
			""")
			.andExpect(status().isUnprocessableEntity());
		assertThat(taskMapper.selectById(task.getId()).getTitle()).isEqualTo("Original");
		assertThat(taskMapper.selectById(task.getId()).getStatus()).isEqualTo(TaskStatus.NOT_STARTED);
	}

	@Test
	void fullPutRequiresEveryEditableFieldRejectsNullNonNullableAndClearsNullableFields() throws Exception {
		User owner = insertUser(SystemRole.USER, true, "Owner");
		Project project = insertProject(owner);
		Task parent = insertTask(project, owner, owner, null, "Parent", TaskStatus.NOT_STARTED);
		Task task = insertTask(project, owner, owner, parent, "Original", TaskStatus.IN_PROGRESS);
		task.setDescription("Original description");
		task.setPriority(Priority.HIGH);
		task.setDueDate(LocalDate.of(2026, 7, 15));
		taskMapper.updateById(task);
		Task original = taskMapper.selectById(task.getId());
		LoginSession session = login(owner);
		Map<String, Object> complete = updateBody(original);

		for (String omitted : List.of("parentId", "title", "description", "assigneeId", "status", "priority", "dueDate")) {
			Map<String, Object> body = new LinkedHashMap<>(complete);
			body.remove(omitted);
			expectProblem(taskRequest(put("/api/tasks/" + task.getId()), session, body),
				422, "VALIDATION_ERROR", "/api/tasks/" + task.getId())
				.andExpect(jsonPath("$.errors").isNotEmpty());
			assertTaskEditableFieldsUnchanged(task.getId(), original);
		}

		for (String nonNullable : List.of("title", "status", "priority")) {
			Map<String, Object> body = new LinkedHashMap<>(complete);
			body.put(nonNullable, null);
			expectProblem(taskRequest(put("/api/tasks/" + task.getId()), session, body),
				422, "VALIDATION_ERROR", "/api/tasks/" + task.getId())
				.andExpect(jsonPath("$.errors").isNotEmpty());
			assertTaskEditableFieldsUnchanged(task.getId(), original);
		}

		Map<String, Object> clearNullable = new LinkedHashMap<>(complete);
		clearNullable.put("parentId", null);
		clearNullable.put("description", null);
		clearNullable.put("assigneeId", null);
		clearNullable.put("dueDate", null);
		taskRequest(put("/api/tasks/" + task.getId()), session, clearNullable)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.parentId").value((Object) null))
			.andExpect(jsonPath("$.description").value((Object) null))
			.andExpect(jsonPath("$.assignee").value((Object) null))
			.andExpect(jsonPath("$.dueDate").value((Object) null));
		Task cleared = taskMapper.selectById(task.getId());
		assertThat(cleared.getParentId()).isNull();
		assertThat(cleared.getDescription()).isNull();
		assertThat(cleared.getAssigneeId()).isNull();
		assertThat(cleared.getDueDate()).isNull();
	}

	@Test
	void ownerCannotReopenTerminalTaskWithRemovedInactiveAdminOrNonUserAssignee() throws Exception {
		User owner = insertUser(SystemRole.USER, true, "Owner");
		User removed = insertUser(SystemRole.USER, true, "Removed");
		User inactive = insertUser(SystemRole.USER, false, "Inactive");
		User admin = insertUser(SystemRole.ADMIN, true, "Admin");
		Project project = insertProject(owner);
		addMember(project, removed);
		addMember(project, inactive);
		Task removedTask = insertTask(project, owner, removed, null, "Removed", TaskStatus.COMPLETED);
		Task inactiveTask = insertTask(project, owner, inactive, null, "Inactive", TaskStatus.COMPLETED);
		Task adminTask = insertTask(project, owner, admin, null, "Admin", TaskStatus.COMPLETED);
		projectMemberMapper.delete(Wrappers.<ProjectMember>lambdaQuery()
			.eq(ProjectMember::getProjectId, project.getId())
			.eq(ProjectMember::getUserId, removed.getId()));
		LoginSession session = login(owner);

		assertReopenRejected(session, removedTask);
		assertReopenRejected(session, inactiveTask);
		assertReopenRejected(session, adminTask);
	}

	@Test
	void ownerCanReopenTerminalTaskWithValidOrNullAssignee() throws Exception {
		User owner = insertUser(SystemRole.USER, true, "Owner");
		User valid = insertUser(SystemRole.USER, true, "Valid");
		Project project = insertProject(owner);
		addMember(project, valid);
		Task assigned = insertTask(project, owner, valid, null, "Assigned", TaskStatus.COMPLETED);
		Task unassigned = insertTask(project, owner, null, null, "Unassigned", TaskStatus.CANCELLED);
		LoginSession session = login(owner);

		taskRequest(put("/api/tasks/" + assigned.getId() + "/status"), session,
			new StatusBody(TaskStatus.IN_PROGRESS))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("IN_PROGRESS"));
		taskRequest(put("/api/tasks/" + unassigned.getId() + "/status"), session,
			new StatusBody(TaskStatus.IN_PROGRESS))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("IN_PROGRESS"));
	}

	@Test
	void csrfIsRequiredForTaskWritesAndFailureDoesNotCreateTask() throws Exception {
		User owner = insertUser(SystemRole.USER, true, "Owner");
		Project project = insertProject(owner);
		LoginSession session = login(owner);
		long before = taskMapper.selectCount(Wrappers.<Task>lambdaQuery()
			.eq(Task::getProjectId, project.getId()));
		mockMvc.perform(post("/api/tasks")
			.session(session.session())
			.contentType(MediaType.APPLICATION_JSON)
			.content(objectMapper.writeValueAsBytes(body(project, null, owner))))
			.andExpect(status().isForbidden())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.type").value("about:blank"))
			.andExpect(jsonPath("$.title").isNotEmpty())
			.andExpect(jsonPath("$.status").value(403))
			.andExpect(jsonPath("$.detail").isNotEmpty())
			.andExpect(jsonPath("$.instance").value("/api/tasks"))
			.andExpect(jsonPath("$.code").value("CSRF_INVALID"))
			.andExpect(jsonPath("$.errors").isArray());
		assertThat(taskMapper.selectCount(Wrappers.<Task>lambdaQuery()
			.eq(Task::getProjectId, project.getId()))).isEqualTo(before);
	}

	@Test
	void listCombinesFiltersPaginatesSortsStablyAndIsolatesVisibility() throws Exception {
		User owner = insertUser(SystemRole.USER, true, "Owner");
		User otherOwner = insertUser(SystemRole.USER, true, "Other Owner");
		User admin = insertUser(SystemRole.ADMIN, true, "Admin");
		Project project = insertProject(owner);
		Project hiddenProject = insertProject(otherOwner);
		Task alpha = insertTask(project, owner, owner, null, "Alpha match", TaskStatus.IN_PROGRESS);
		alpha.setPriority(Priority.HIGH);
		alpha.setDueDate(LocalDate.of(2026, 7, 10));
		taskMapper.updateById(alpha);
		Task beta = insertTask(project, owner, owner, null, "Beta match", TaskStatus.IN_PROGRESS);
		beta.setPriority(Priority.HIGH);
		beta.setDueDate(LocalDate.of(2026, 7, 20));
		taskMapper.updateById(beta);
		Task hidden = insertTask(hiddenProject, otherOwner, otherOwner, null, "Hidden match", TaskStatus.IN_PROGRESS);
		insertLog(alpha, owner, 20, LocalDateTime.of(2026, 7, 10, 9, 0));
		insertLog(alpha, owner, 70, LocalDateTime.of(2026, 7, 11, 9, 0));

		LoginSession ownerSession = login(owner);
		taskRequest(get("/api/tasks?projectId=" + project.getId()
			+ "&assigneeId=" + owner.getId()
			+ "&status=IN_PROGRESS&priority=HIGH&q=match"
			+ "&dueAfter=2026-07-01&dueBefore=2026-07-31&page=0&size=1&sort=title,desc"),
			ownerSession, null)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].id").value(beta.getId().toString()))
			.andExpect(jsonPath("$.page").value(0))
			.andExpect(jsonPath("$.size").value(1))
			.andExpect(jsonPath("$.totalElements").value(2))
			.andExpect(jsonPath("$.totalPages").value(2));
		taskRequest(get("/api/tasks?projectId=" + project.getId() + "&sort=title,asc"),
			ownerSession, null)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].id").value(alpha.getId().toString()))
			.andExpect(jsonPath("$.items[0].progressPercent").value(70));
		taskRequest(get("/api/tasks?projectId=" + hiddenProject.getId()), ownerSession, null)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items").isEmpty())
			.andExpect(jsonPath("$.totalElements").value(0));
		taskRequest(get("/api/tasks?projectId=" + hiddenProject.getId()), login(admin), null)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].id").value(hidden.getId().toString()));

		taskRequest(get("/api/tasks?page=-1"), ownerSession, null)
			.andExpect(status().isUnprocessableEntity());
		taskRequest(get("/api/tasks?size=101"), ownerSession, null)
			.andExpect(status().isUnprocessableEntity());
		taskRequest(get("/api/tasks?sort=creatorId,asc"), ownerSession, null)
			.andExpect(status().isUnprocessableEntity());
		taskRequest(get("/api/tasks?sort=title,sideways"), ownerSession, null)
			.andExpect(status().isUnprocessableEntity());
	}

	@Test
	void listAndsParentAssigneeStatusPriorityTextAndDateFiltersAndHidesOtherProjects() throws Exception {
		User owner = insertUser(SystemRole.USER, true, "Owner");
		User member = insertUser(SystemRole.USER, true, "Member");
		User otherOwner = insertUser(SystemRole.USER, true, "Other Owner");
		Project project = insertProject(owner);
		addMember(project, member);
		Project hiddenProject = insertProject(otherOwner);
		Task parent = insertTask(project, owner, owner, null, "Parent", TaskStatus.NOT_STARTED);
		Task alternateParent = insertTask(project, owner, owner, null, "Alternate Parent", TaskStatus.NOT_STARTED);
		Task match = configuredTask(project, owner, owner, parent, "Filter needle", TaskStatus.IN_PROGRESS,
			Priority.HIGH, LocalDate.of(2026, 7, 15));
		configuredTask(project, owner, owner, alternateParent, "Filter needle", TaskStatus.IN_PROGRESS,
			Priority.HIGH, LocalDate.of(2026, 7, 15));
		configuredTask(project, owner, member, parent, "Filter needle", TaskStatus.IN_PROGRESS,
			Priority.HIGH, LocalDate.of(2026, 7, 15));
		configuredTask(project, owner, owner, parent, "Filter needle", TaskStatus.BLOCKED,
			Priority.HIGH, LocalDate.of(2026, 7, 15));
		configuredTask(project, owner, owner, parent, "Filter needle", TaskStatus.IN_PROGRESS,
			Priority.LOW, LocalDate.of(2026, 7, 15));
		configuredTask(project, owner, owner, parent, "Other text", TaskStatus.IN_PROGRESS,
			Priority.HIGH, LocalDate.of(2026, 7, 15));
		configuredTask(project, owner, owner, parent, "Filter needle", TaskStatus.IN_PROGRESS,
			Priority.HIGH, LocalDate.of(2026, 7, 25));
		Task hiddenParent = insertTask(hiddenProject, otherOwner, otherOwner, null, "Hidden Parent", TaskStatus.NOT_STARTED);
		Task hidden = configuredTask(hiddenProject, otherOwner, otherOwner, hiddenParent, "Filter needle",
			TaskStatus.IN_PROGRESS, Priority.HIGH, LocalDate.of(2026, 7, 15));

		String filters = "projectId=" + project.getId()
			+ "&parentId=" + parent.getId()
			+ "&assigneeId=" + owner.getId()
			+ "&status=IN_PROGRESS&priority=HIGH&q=needle"
			+ "&dueAfter=2026-07-01&dueBefore=2026-07-20";
		taskRequest(get("/api/tasks?" + filters), login(owner), null)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items").isArray())
			.andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.items[0].id").value(match.getId().toString()))
			.andExpect(jsonPath("$.totalElements").value(1));

		taskRequest(get("/api/tasks?projectId=" + hiddenProject.getId()
			+ "&parentId=" + hiddenParent.getId()
			+ "&assigneeId=" + otherOwner.getId()
			+ "&status=IN_PROGRESS&priority=HIGH&q=needle"
			+ "&dueAfter=2026-07-01&dueBefore=2026-07-20"), login(owner), null)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items").isEmpty())
			.andExpect(jsonPath("$.totalElements").value(0));
	}

	@Test
	void equalPrimaryTaskSortValuesUseIdAsStableSecondaryOrderAcrossPages() throws Exception {
		User owner = insertUser(SystemRole.USER, true, "Owner");
		Project project = insertProject(owner);
		List<Task> tasks = List.of(
			insertTask(project, owner, owner, null, "Tie 1", TaskStatus.NOT_STARTED),
			insertTask(project, owner, owner, null, "Tie 2", TaskStatus.NOT_STARTED),
			insertTask(project, owner, owner, null, "Tie 3", TaskStatus.NOT_STARTED),
			insertTask(project, owner, owner, null, "Tie 4", TaskStatus.NOT_STARTED));
		LocalDateTime tiedAt = LocalDateTime.of(2026, 7, 17, 12, 0);
		for (Task task : tasks) {
			taskMapper.update(null, Wrappers.<Task>lambdaUpdate()
				.eq(Task::getId, task.getId())
				.set(Task::getCreatedAt, tiedAt));
		}
		LoginSession session = login(owner);

		List<String> asc = new ArrayList<>();
		asc.addAll(pageTaskIds(session, project, "createdAt,asc", 0, 2));
		asc.addAll(pageTaskIds(session, project, "createdAt,asc", 1, 2));
		assertThat(asc).containsExactlyElementsOf(tasks.stream().map(task -> task.getId().toString()).toList());
		assertThat(asc.subList(0, 2)).doesNotContainAnyElementsOf(asc.subList(2, 4));

		List<String> desc = new ArrayList<>();
		desc.addAll(pageTaskIds(session, project, "createdAt,desc", 0, 2));
		desc.addAll(pageTaskIds(session, project, "createdAt,desc", 1, 2));
		assertThat(desc).containsExactlyElementsOf(tasks.stream()
			.map(task -> task.getId().toString()).toList());
		assertThat(desc.subList(0, 2)).doesNotContainAnyElementsOf(desc.subList(2, 4));
	}

	@Test
	void latestProgressUsesCreatedAtThenLargerIdAndOneBatchForListAndDetail() throws Exception {
		User owner = insertUser(SystemRole.USER, true, "Owner");
		Project project = insertProject(owner);
		Task withLogs = insertTask(project, owner, owner, null, "A Progress", TaskStatus.IN_PROGRESS);
		Task withoutLogs = insertTask(project, owner, owner, null, "Z No logs", TaskStatus.IN_PROGRESS);
		LocalDateTime tiedAt = LocalDateTime.of(2026, 7, 17, 13, 0);
		insertLog(withLogs, owner, 20, tiedAt);
		TaskLog latest = insertLog(withLogs, owner, 80, tiedAt);
		assertThat(latest.getId()).isGreaterThan(0L);
		LoginSession session = login(owner);

		Mockito.clearInvocations(taskMapper);
		taskRequest(get("/api/tasks?projectId=" + project.getId() + "&sort=title,asc"), session, null)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].id").value(withLogs.getId().toString()))
			.andExpect(jsonPath("$.items[0].progressPercent").value(80))
			.andExpect(jsonPath("$.items[1].id").value(withoutLogs.getId().toString()))
			.andExpect(jsonPath("$.items[1].progressPercent").value(0));
		assertLatestProgressBatch(List.of(withLogs.getId(), withoutLogs.getId()));

		Mockito.clearInvocations(taskMapper);
		taskRequest(get("/api/tasks/" + withLogs.getId()), session, null)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.progressPercent").value(80));
		assertLatestProgressBatch(List.of(withLogs.getId()));
	}

	@Test
	void deleteReferenceFailuresKeepTaskAndExactChildLogAndSummaryRows() throws Exception {
		User owner = insertUser(SystemRole.USER, true, "Owner");
		Project project = insertProject(owner);
		LoginSession session = login(owner);

		Task parent = insertTask(project, owner, owner, null, "Parent", TaskStatus.NOT_STARTED);
		Task child = insertTask(project, owner, owner, parent, "Child", TaskStatus.NOT_STARTED);
		assertResourceInUse(session, parent);
		assertThat(taskMapper.selectById(parent.getId()).getTitle()).isEqualTo("Parent");
		assertThat(taskMapper.selectById(child.getId()).getParentId()).isEqualTo(parent.getId());

		Task logged = insertTask(project, owner, owner, null, "Logged", TaskStatus.NOT_STARTED);
		TaskLog log = insertLog(logged, owner, 10, LocalDateTime.of(2026, 7, 17, 14, 0));
		assertResourceInUse(session, logged);
		assertThat(taskMapper.selectById(logged.getId()).getTitle()).isEqualTo("Logged");
		assertThat(taskLogMapper.selectById(log.getId()).getTaskId()).isEqualTo(logged.getId());
		assertThat(taskLogMapper.selectById(log.getId()).getContent()).isEqualTo("Progress 10");

		Task summarized = insertTask(project, owner, owner, null, "Summarized", TaskStatus.NOT_STARTED);
		Summary summary = new Summary();
		summary.setProjectId(project.getId());
		summary.setTaskId(summarized.getId());
		summary.setCreatedBy(owner.getId());
		summary.setType(SummaryType.STAGE);
		summary.setContent("Reference");
		summaryMapper.insert(summary);
		assertResourceInUse(session, summarized);
		assertThat(taskMapper.selectById(summarized.getId()).getTitle()).isEqualTo("Summarized");
		assertThat(summaryMapper.selectById(summary.getId()).getTaskId()).isEqualTo(summarized.getId());
		assertThat(summaryMapper.selectById(summary.getId()).getContent()).isEqualTo("Reference");
	}

	@Test
	void recognizedTaskReferenceConstraintFailuresBecomeResourceInUse() throws Exception {
		User owner = insertUser(SystemRole.USER, true, "Owner");
		Project project = insertProject(owner);
		LoginSession session = login(owner);
		for (String constraint : List.of("fk_tasks_parent", "fk_task_logs_task", "fk_summaries_task")) {
			Task task = insertTask(project, owner, owner, null, "Constraint " + constraint, TaskStatus.NOT_STARTED);
			doThrow(new DataIntegrityViolationException("constraint " + constraint))
				.when(taskMapper).deleteById(task.getId());
			expectProblem(taskRequest(delete("/api/tasks/" + task.getId()), session, null),
				409, "RESOURCE_IN_USE", "/api/tasks/" + task.getId());
			assertThat(taskMapper.selectById(task.getId())).isNotNull();
			Mockito.reset(taskMapper);
		}
	}

	@Test
	void deletionProtectsEveryDocumentedReferenceAndKeepsTheTask() throws Exception {
		User owner = insertUser(SystemRole.USER, true, "Owner");
		Project project = insertProject(owner);
		LoginSession session = login(owner);

		Task withChild = insertTask(project, owner, owner, null, "With Child", TaskStatus.NOT_STARTED);
		insertTask(project, owner, owner, withChild, "Child", TaskStatus.NOT_STARTED);
		assertResourceInUse(session, withChild);

		Task withLog = insertTask(project, owner, owner, null, "With Log", TaskStatus.NOT_STARTED);
		insertLog(withLog, owner, 10, LocalDateTime.now());
		assertResourceInUse(session, withLog);

		Task withSummary = insertTask(project, owner, owner, null, "With Summary", TaskStatus.NOT_STARTED);
		Summary summary = new Summary();
		summary.setProjectId(project.getId());
		summary.setTaskId(withSummary.getId());
		summary.setCreatedBy(owner.getId());
		summary.setType(SummaryType.STAGE);
		summary.setContent("Reference");
		summaryMapper.insert(summary);
		assertResourceInUse(session, withSummary);
	}

	@Test
	void unrelatedIntegrityFailureIsNotConvertedToResourceInUse() throws Exception {
		User owner = insertUser(SystemRole.USER, true, "Owner");
		Project project = insertProject(owner);
		Task task = insertTask(project, owner, owner, null, "Database Failure", TaskStatus.NOT_STARTED);
		doThrow(new DataIntegrityViolationException("constraint chk_tasks_status"))
			.when(taskMapper).deleteById(task.getId());

		expectProblem(taskRequest(delete("/api/tasks/" + task.getId()), login(owner), null),
			500, "INTERNAL_SERVER_ERROR", "/api/tasks/" + task.getId());
		assertThat(taskMapper.selectById(task.getId())).isNotNull();
	}

	private ResultActions expectProblem(
		ResultActions result,
		int expectedStatus,
		String code,
		String instance) throws Exception {
		return result
			.andExpect(status().is(expectedStatus))
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.type").value("about:blank"))
			.andExpect(jsonPath("$.title").isNotEmpty())
			.andExpect(jsonPath("$.status").value(expectedStatus))
			.andExpect(jsonPath("$.detail").isNotEmpty())
			.andExpect(jsonPath("$.instance").value(instance))
			.andExpect(jsonPath("$.code").value(code))
			.andExpect(jsonPath("$.errors").isArray());
	}

	private Task configuredTask(
		Project project,
		User creator,
		User assignee,
		Task parent,
		String title,
		TaskStatus status,
		Priority priority,
		LocalDate dueDate) {
		Task task = insertTask(project, creator, assignee, parent, title, status);
		task.setPriority(priority);
		task.setDueDate(dueDate);
		taskMapper.updateById(task);
		return taskMapper.selectById(task.getId());
	}

	private List<String> pageTaskIds(
		LoginSession session,
		Project project,
		String sort,
		int page,
		int size) throws Exception {
		MvcResult result = taskRequest(get("/api/tasks?projectId=" + project.getId()
			+ "&sort=" + sort + "&page=" + page + "&size=" + size), session, null)
			.andExpect(status().isOk())
			.andReturn();
		JsonNode items = objectMapper.readTree(result.getResponse().getContentAsByteArray()).get("items");
		List<String> ids = new ArrayList<>();
		items.forEach(item -> ids.add(item.get("id").asText()));
		return ids;
	}

	private void assertLatestProgressBatch(List<Long> expectedTaskIds) {
		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<Long>> captor = (ArgumentCaptor<List<Long>>) (ArgumentCaptor<?>)
			ArgumentCaptor.forClass(List.class);
		verify(taskMapper, times(1)).selectLatestProgressByTaskIds(captor.capture());
		assertThat(captor.getValue()).containsExactlyInAnyOrderElementsOf(expectedTaskIds);
	}

	private static Map<String, Object> updateBody(Task task) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("parentId", task.getParentId() == null ? null : task.getParentId().toString());
		body.put("title", task.getTitle());
		body.put("description", task.getDescription());
		body.put("assigneeId", task.getAssigneeId() == null ? null : task.getAssigneeId().toString());
		body.put("status", task.getStatus());
		body.put("priority", task.getPriority());
		body.put("dueDate", task.getDueDate());
		return body;
	}

	private static Map<String, Object> updateBody(Project project, Task parent, User assignee) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("parentId", parent == null ? null : parent.getId().toString());
		body.put("title", "HTTP Task");
		body.put("description", "HTTP task description");
		body.put("assigneeId", assignee == null ? null : assignee.getId().toString());
		body.put("status", TaskStatus.NOT_STARTED);
		body.put("priority", Priority.HIGH);
		body.put("dueDate", LocalDate.of(2026, 7, 15));
		return body;
	}

	private void assertTaskEditableFieldsUnchanged(Long taskId, Task expected) {
		Task actual = taskMapper.selectById(taskId);
		assertThat(actual.getParentId()).isEqualTo(expected.getParentId());
		assertThat(actual.getTitle()).isEqualTo(expected.getTitle());
		assertThat(actual.getDescription()).isEqualTo(expected.getDescription());
		assertThat(actual.getAssigneeId()).isEqualTo(expected.getAssigneeId());
		assertThat(actual.getStatus()).isEqualTo(expected.getStatus());
		assertThat(actual.getPriority()).isEqualTo(expected.getPriority());
		assertThat(actual.getDueDate()).isEqualTo(expected.getDueDate());
	}

	private void assertReopenRejected(LoginSession session, Task task) throws Exception {
		expectProblem(taskRequest(put("/api/tasks/" + task.getId() + "/status"), session,
			new StatusBody(TaskStatus.IN_PROGRESS)),
			422, "VALIDATION_ERROR", "/api/tasks/" + task.getId() + "/status");
		assertThat(taskMapper.selectById(task.getId()).getStatus()).isEqualTo(TaskStatus.COMPLETED);
	}

	private void assertResourceInUse(LoginSession session, Task task) throws Exception {
		taskRequest(delete("/api/tasks/" + task.getId()), session, null)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("RESOURCE_IN_USE"));
		assertThat(taskMapper.selectById(task.getId())).isNotNull();
	}

	private void assertCreateError(
		LoginSession session,
		TaskBody body,
		String code,
		int statusCode) throws Exception {
		long before = taskMapper.selectCount(Wrappers.<Task>lambdaQuery()
			.eq(Task::getProjectId, Long.parseLong(body.projectId())));
		taskRequest(post("/api/tasks"), session, body)
			.andExpect(status().is(statusCode))
			.andExpect(jsonPath("$.code").value(code));
		assertThat(taskMapper.selectCount(Wrappers.<Task>lambdaQuery()
			.eq(Task::getProjectId, Long.parseLong(body.projectId())))).isEqualTo(before);
	}

	private ResultActions taskRequest(
		org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
		LoginSession session,
		Object body) throws Exception {
		request.session(session.session())
			.header(session.headerName(), session.token());
		if (body != null) {
			request.contentType(MediaType.APPLICATION_JSON);
			request.content(body instanceof String value
				? value
				: objectMapper.writeValueAsString(body));
		}
		return mockMvc.perform(request);
	}

	private LoginSession login(User user) throws Exception {
		MvcResult csrfResult = mockMvc.perform(get("/api/auth/csrf")).andReturn();
		JsonNode csrf = objectMapper.readTree(csrfResult.getResponse().getContentAsByteArray());
		MockHttpSession session = (MockHttpSession) csrfResult.getRequest().getSession(false);
		mockMvc.perform(post("/api/auth/login")
				.session(session)
				.header(csrf.get("headerName").asText(), csrf.get("token").asText())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsBytes(
					new LoginBody(user.getUsername(), "test-password"))))
			.andExpect(status().isOk());
		return new LoginSession(session, csrf.get("headerName").asText(), csrf.get("token").asText());
	}

	private User insertUser(SystemRole role, boolean active, String displayName) {
		User user = new User();
		user.setUsername("task-http-" + UUID.randomUUID());
		user.setPasswordHash(passwordEncoder.encode("test-password"));
		user.setDisplayName(displayName);
		user.setSystemRole(role);
		user.setActive(active);
		userMapper.insert(user);
		userIds.add(user.getId());
		return userMapper.selectById(user.getId());
	}

	private Project insertProject(User owner) {
		Project project = new Project();
		project.setOwnerId(owner.getId());
		project.setName("Task HTTP " + UUID.randomUUID());
		project.setStatus(ProjectStatus.IN_PROGRESS);
		project.setPriority(Priority.MEDIUM);
		project.setStartDate(LocalDate.of(2026, 7, 1));
		project.setEndDate(LocalDate.of(2026, 7, 31));
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

	private Task insertTask(
		Project project,
		User creator,
		User assignee,
		Task parent,
		String title,
		TaskStatus status) {
		Task task = new Task();
		task.setProjectId(project.getId());
		task.setParentId(parent == null ? null : parent.getId());
		task.setAssigneeId(assignee == null ? null : assignee.getId());
		task.setCreatorId(creator.getId());
		task.setTitle(title);
		task.setDescription("HTTP task description");
		task.setStatus(status);
		task.setPriority(Priority.MEDIUM);
		task.setDueDate(LocalDate.of(2026, 7, 15));
		taskMapper.insert(task);
		return taskMapper.selectById(task.getId());
	}

	private TaskLog insertLog(Task task, User operator, int progress, LocalDateTime createdAt) {
		TaskLog log = new TaskLog();
		log.setTaskId(task.getId());
		log.setOperatorId(operator.getId());
		log.setProgressPercent(progress);
		log.setContent("Progress " + progress);
		taskLogMapper.insert(log);
		if (createdAt != null) {
			taskLogMapper.update(null, Wrappers.<TaskLog>lambdaUpdate()
				.eq(TaskLog::getId, log.getId())
				.set(TaskLog::getCreatedAt, createdAt));
		}
		return taskLogMapper.selectById(log.getId());
	}

	private static TaskBody body(Project project, Task parent, User assignee) {
		return new TaskBody(
			project.getId().toString(),
			parent == null ? null : parent.getId().toString(),
			"HTTP Task",
			"HTTP task description",
			assignee == null ? null : assignee.getId().toString(),
			TaskStatus.NOT_STARTED,
			Priority.HIGH,
			LocalDate.of(2026, 7, 15));
	}

	record LoginBody(String username, String password) {
	}

	record LoginSession(MockHttpSession session, String headerName, String token) {
	}

	record StatusBody(TaskStatus status) {
	}

	record TaskBody(
		String projectId,
		String parentId,
		String title,
		String description,
		String assigneeId,
		TaskStatus status,
		Priority priority,
		LocalDate dueDate) {

		TaskBody withAssignee(String value) {
			return new TaskBody(projectId, parentId, title, description, value, status, priority, dueDate);
		}

		TaskBody withDueDate(LocalDate value) {
			return new TaskBody(projectId, parentId, title, description, assigneeId, status, priority, value);
		}
	}
}
