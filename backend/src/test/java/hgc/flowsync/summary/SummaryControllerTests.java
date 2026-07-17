package hgc.flowsync.summary;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import hgc.flowsync.common.error.BusinessException;
import hgc.flowsync.common.error.ErrorCode;
import hgc.flowsync.project.Priority;
import hgc.flowsync.project.Project;
import hgc.flowsync.project.ProjectAccessService;
import hgc.flowsync.project.ProjectMapper;
import hgc.flowsync.project.ProjectMember;
import hgc.flowsync.project.ProjectMemberMapper;
import hgc.flowsync.project.ProjectStatus;
import hgc.flowsync.task.Task;
import hgc.flowsync.task.TaskMapper;
import hgc.flowsync.task.TaskStatus;
import hgc.flowsync.user.CurrentUserService;
import hgc.flowsync.user.SystemRole;
import hgc.flowsync.user.User;
import hgc.flowsync.user.UserMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hamcrest.Matchers;
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
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SummaryControllerTests {

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
	@MockitoSpyBean
	private SummaryMapper summaryMapper;
	@Autowired
	private SummaryService summaryService;
	@Autowired
	private SummaryAccessService summaryAccessService;
	@Autowired
	private CurrentUserService currentUserService;
	@Autowired
	private ProjectAccessService projectAccessService;
	@Autowired
	private PasswordEncoder passwordEncoder;
	@Autowired
	private PlatformTransactionManager transactionManager;

	private final List<Long> projectIds = new ArrayList<>();
	private final List<Long> userIds = new ArrayList<>();

	@AfterEach
	void deleteCreatedRecords() {
		Mockito.reset(summaryMapper);
		if (!projectIds.isEmpty()) {
			summaryMapper.delete(Wrappers.<Summary>lambdaQuery()
				.in(Summary::getProjectId, projectIds));
			taskMapper.delete(Wrappers.<Task>lambdaQuery()
				.in(Task::getProjectId, projectIds));
			projectMemberMapper.delete(Wrappers.<ProjectMember>lambdaQuery()
				.in(ProjectMember::getProjectId, projectIds));
			projectMapper.delete(Wrappers.<Project>lambdaQuery()
				.in(Project::getId, projectIds));
		}
		if (!userIds.isEmpty()) {
			userMapper.delete(Wrappers.<User>lambdaQuery().in(User::getId, userIds));
		}
	}

	@Test
	void memberCanUseAllFiveEndpointsForProjectAndTaskSummaries() throws Exception {
		User owner = insertUser(SystemRole.USER, "Owner");
		User member = insertUser(SystemRole.USER, "Summary Author");
		Project project = insertProject(owner, ProjectStatus.IN_PROGRESS);
		addMember(project, member);
		Task task = insertTask(project, owner);
		LoginSession session = login(member);

		MvcResult created = summaryRequest(post("/api/summaries"), session,
			body(project, task, SummaryType.STAGE, "Task stage"))
			.andExpect(status().isCreated())
			.andExpect(content().contentType(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.id", Matchers.instanceOf(String.class)))
			.andExpect(jsonPath("$.projectId").value(project.getId().toString()))
			.andExpect(jsonPath("$.taskId").value(task.getId().toString()))
			.andExpect(jsonPath("$.createdBy.id").value(member.getId().toString()))
			.andExpect(jsonPath("$.createdBy.displayName").value("Summary Author"))
			.andExpect(jsonPath("$.type").value("STAGE"))
			.andExpect(jsonPath("$.content").value("Task stage"))
			.andExpect(jsonPath("$.createdAt").value(Matchers.endsWith("Z")))
			.andExpect(jsonPath("$.updatedAt").value(Matchers.endsWith("Z")))
			.andExpect(jsonPath("$.createdBy.username").doesNotExist())
			.andReturn();
		String summaryId = objectMapper.readTree(created.getResponse().getContentAsByteArray())
			.get("id").asText();

		summaryRequest(get("/api/summaries?projectId=" + project.getId()), session, null)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].id").value(summaryId));
		summaryRequest(get("/api/summaries/" + summaryId), session, null)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content").value("Task stage"));
		summaryRequest(put("/api/summaries/" + summaryId), session,
			new UpdateBody(SummaryType.FINAL, "Task final"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.type").value("FINAL"));
		summaryRequest(put("/api/summaries/" + summaryId), session,
			new UpdateBody(SummaryType.STAGE, "Task stage again"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.type").value("STAGE"));
		summaryRequest(delete("/api/summaries/" + summaryId), session, null)
			.andExpect(status().isNoContent())
			.andExpect(content().string(""));
		assertThat(summaryMapper.selectById(summaryId)).isNull();

		summaryRequest(post("/api/summaries"), session,
			body(project, null, SummaryType.FINAL, "Project final"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.taskId").value((Object) null));
	}

	@Test
	void duplicateSummariesAreAllowed() throws Exception {
		User owner = insertUser(SystemRole.USER, "Owner");
		Project project = insertProject(owner, ProjectStatus.IN_PROGRESS);
		LoginSession session = login(owner);
		CreateBody body = body(project, null, SummaryType.FINAL, "Same scope and type");

		summaryRequest(post("/api/summaries"), session, body).andExpect(status().isCreated());
		summaryRequest(post("/api/summaries"), session, body).andExpect(status().isCreated());

		assertThat(summaryMapper.selectCount(Wrappers.<Summary>lambdaQuery()
			.eq(Summary::getProjectId, project.getId())
			.isNull(Summary::getTaskId)
			.eq(Summary::getType, SummaryType.FINAL))).isEqualTo(2);
	}

	@Test
	void authenticationAndCsrfAreEnforced() throws Exception {
		User owner = insertUser(SystemRole.USER, "Owner");
		Project project = insertProject(owner, ProjectStatus.IN_PROGRESS);
		LoginSession session = login(owner);

		mockMvc.perform(get("/api/summaries")).andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/summaries")
				.session(session.session())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsBytes(
					body(project, null, SummaryType.STAGE, "No CSRF"))))
			.andExpect(status().isForbidden())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("CSRF_INVALID"));
	}

	@Test
	void visibilityAndWritePermissionsMatchTheFrozenMatrix() throws Exception {
		User owner = insertUser(SystemRole.USER, "Owner");
		User creator = insertUser(SystemRole.USER, "Creator");
		User member = insertUser(SystemRole.USER, "Member");
		User outsider = insertUser(SystemRole.USER, "Outsider");
		User admin = insertUser(SystemRole.ADMIN, "Admin");
		Project project = insertProject(owner, ProjectStatus.IN_PROGRESS);
		addMember(project, creator);
		addMember(project, member);
		Summary summary = insertSummary(project, null, creator, SummaryType.STAGE, "Protected");
		Summary ownerDeletes = insertSummary(project, null, creator, SummaryType.STAGE, "Owner deletes");

		LoginSession adminSession = login(admin);
		summaryRequest(get("/api/summaries?projectId=" + project.getId()), adminSession, null)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].id").value(summary.getId().toString()));
		summaryRequest(get("/api/summaries/" + summary.getId()), adminSession, null)
			.andExpect(status().isOk());
		summaryRequest(post("/api/summaries"), adminSession,
			body(project, null, SummaryType.STAGE, "Admin write"))
			.andExpect(status().isForbidden());
		summaryRequest(put("/api/summaries/" + summary.getId()), adminSession,
			new UpdateBody(SummaryType.FINAL, "Admin update"))
			.andExpect(status().isForbidden());
		summaryRequest(delete("/api/summaries/" + summary.getId()), adminSession, null)
			.andExpect(status().isForbidden());

		LoginSession outsiderSession = login(outsider);
		summaryRequest(get("/api/summaries?projectId=" + project.getId()), outsiderSession, null)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(0));
		summaryRequest(get("/api/summaries/" + summary.getId()), outsiderSession, null)
			.andExpect(status().isNotFound());
		summaryRequest(put("/api/summaries/" + summary.getId()), outsiderSession,
			new UpdateBody(SummaryType.FINAL, "Outsider update"))
			.andExpect(status().isNotFound());
		summaryRequest(delete("/api/summaries/" + summary.getId()), outsiderSession, null)
			.andExpect(status().isNotFound());
		summaryRequest(post("/api/summaries"), outsiderSession,
			body(project, null, SummaryType.STAGE, "Outsider create"))
			.andExpect(status().isNotFound());

		LoginSession memberSession = login(member);
		summaryRequest(put("/api/summaries/" + summary.getId()), memberSession,
			new UpdateBody(SummaryType.FINAL, "Member update"))
			.andExpect(status().isForbidden());
		summaryRequest(delete("/api/summaries/" + summary.getId()), memberSession, null)
			.andExpect(status().isForbidden());

		summaryRequest(put("/api/summaries/" + summary.getId()), login(owner),
			new UpdateBody(SummaryType.FINAL, "Owner update"))
			.andExpect(status().isOk());
		summaryRequest(delete("/api/summaries/" + ownerDeletes.getId()), login(owner), null)
			.andExpect(status().isNoContent());
		summaryRequest(delete("/api/summaries/" + summary.getId()), login(creator), null)
			.andExpect(status().isNoContent());
	}

	@Test
	void removedCreatorLosesReadUpdateAndDeleteVisibility() throws Exception {
		User owner = insertUser(SystemRole.USER, "Owner");
		User creator = insertUser(SystemRole.USER, "Removed creator");
		Project project = insertProject(owner, ProjectStatus.IN_PROGRESS);
		addMember(project, creator);
		Summary summary = insertSummary(project, null, creator, SummaryType.STAGE, "Before removal");
		projectMemberMapper.delete(Wrappers.<ProjectMember>lambdaQuery()
			.eq(ProjectMember::getProjectId, project.getId())
			.eq(ProjectMember::getUserId, creator.getId()));
		LoginSession session = login(creator);

		summaryRequest(get("/api/summaries/" + summary.getId()), session, null)
			.andExpect(status().isNotFound());
		summaryRequest(put("/api/summaries/" + summary.getId()), session,
			new UpdateBody(SummaryType.FINAL, "After removal"))
			.andExpect(status().isNotFound());
		summaryRequest(delete("/api/summaries/" + summary.getId()), session, null)
			.andExpect(status().isNotFound());
		assertThat(summaryMapper.selectById(summary.getId()).getContent()).isEqualTo("Before removal");
	}

	@Test
	void archivedProjectRemainsReadableButRejectsEveryWrite() throws Exception {
		User owner = insertUser(SystemRole.USER, "Owner");
		Project project = insertProject(owner, ProjectStatus.COMPLETED);
		Summary summary = insertSummary(project, null, owner, SummaryType.FINAL, "Archived content");
		projectMapper.update(null, Wrappers.<Project>lambdaUpdate()
			.eq(Project::getId, project.getId())
			.set(Project::getArchivedAt, LocalDateTime.now()));
		LoginSession session = login(owner);

		summaryRequest(get("/api/summaries?projectId=" + project.getId()), session, null)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(1));
		summaryRequest(get("/api/summaries/" + summary.getId()), session, null)
			.andExpect(status().isOk());
		assertProblem(summaryRequest(post("/api/summaries"), session,
			body(project, null, SummaryType.STAGE, "Archived create")),
			409, "PROJECT_ARCHIVED");
		assertProblem(summaryRequest(put("/api/summaries/" + summary.getId()), session,
			new UpdateBody(SummaryType.STAGE, "Archived update")),
			409, "PROJECT_ARCHIVED");
		assertProblem(summaryRequest(delete("/api/summaries/" + summary.getId()), session, null),
			409, "PROJECT_ARCHIVED");
		assertThat(summaryMapper.selectById(summary.getId()).getContent()).isEqualTo("Archived content");
	}

	@Test
	void everyActiveProjectStatusAllowsSummaryWrites() throws Exception {
		User owner = insertUser(SystemRole.USER, "Owner");
		LoginSession session = login(owner);
		for (ProjectStatus status : ProjectStatus.values()) {
			Project project = insertProject(owner, status);
			MvcResult result = summaryRequest(post("/api/summaries"), session,
				body(project, null, SummaryType.STAGE, "Status " + status))
				.andExpect(status().isCreated())
				.andReturn();
			String id = objectMapper.readTree(result.getResponse().getContentAsByteArray()).get("id").asText();
			summaryRequest(put("/api/summaries/" + id), session,
				new UpdateBody(SummaryType.FINAL, "Updated " + status))
				.andExpect(status().isOk());
			summaryRequest(delete("/api/summaries/" + id), session, null)
				.andExpect(status().isNoContent());
		}
	}

	@Test
	void taskAssociationMustExistAndBelongToTheProject() throws Exception {
		User owner = insertUser(SystemRole.USER, "Owner");
		Project project = insertProject(owner, ProjectStatus.IN_PROGRESS);
		Project other = insertProject(owner, ProjectStatus.IN_PROGRESS);
		Task foreignTask = insertTask(other, owner);
		LoginSession session = login(owner);
		long before = summaryMapper.selectCount(null);

		assertProblem(summaryRequest(post("/api/summaries"), session,
			new CreateBody(project.getId().toString(), Long.toString(Long.MAX_VALUE),
				SummaryType.STAGE, "Missing task")), 404, "NOT_FOUND");
		assertProblem(summaryRequest(post("/api/summaries"), session,
			body(project, foreignTask, SummaryType.STAGE, "Wrong project")), 404, "NOT_FOUND");
		assertThat(summaryMapper.selectCount(null)).isEqualTo(before);
	}

	@Test
	void fullPutValidationRejectsMissingNullInvalidAndBlankValuesWithoutMutation() throws Exception {
		User owner = insertUser(SystemRole.USER, "Owner");
		Project project = insertProject(owner, ProjectStatus.IN_PROGRESS);
		Summary summary = insertSummary(project, null, owner, SummaryType.STAGE, "Original");
		LoginSession session = login(owner);

		for (String body : List.of(
			"{\"content\":\"Missing type\"}",
			"{\"type\":\"FINAL\"}",
			"{\"type\":null,\"content\":\"Null type\"}",
			"{\"type\":\"FINAL\",\"content\":null}",
			"{\"type\":\"FINAL\",\"content\":\"\"}",
			"{\"type\":\"FINAL\",\"content\":\"   \"}",
			"{\"type\":\"UNKNOWN\",\"content\":\"Invalid enum\"}")) {
			assertProblem(summaryRequest(put("/api/summaries/" + summary.getId()), session, body),
				422, "VALIDATION_ERROR");
			Summary unchanged = summaryMapper.selectById(summary.getId());
			assertThat(unchanged.getType()).isEqualTo(SummaryType.STAGE);
			assertThat(unchanged.getContent()).isEqualTo("Original");
		}
	}

	@Test
	void createValidationRejectsMissingNullableFieldAndInvalidContent() throws Exception {
		User owner = insertUser(SystemRole.USER, "Owner");
		Project project = insertProject(owner, ProjectStatus.IN_PROGRESS);
		LoginSession session = login(owner);

		for (String body : List.of(
			"{\"projectId\":\"%s\",\"type\":\"STAGE\",\"content\":\"Missing taskId\"}"
				.formatted(project.getId()),
			"{\"projectId\":null,\"taskId\":null,\"type\":\"STAGE\",\"content\":\"No project\"}",
			"{\"projectId\":\"%s\",\"taskId\":null,\"type\":null,\"content\":\"No type\"}"
				.formatted(project.getId()),
			"{\"projectId\":\"%s\",\"taskId\":null,\"type\":\"STAGE\",\"content\":null}"
				.formatted(project.getId()),
			"{\"projectId\":\"%s\",\"taskId\":null,\"type\":\"STAGE\",\"content\":\" \\t \"}"
				.formatted(project.getId()))) {
			assertProblem(summaryRequest(post("/api/summaries"), session, body),
				422, "VALIDATION_ERROR");
		}
	}

	@Test
	void listFiltersAreSingleAndCombinedAndTaskNoneMeansProjectLevel() throws Exception {
		User owner = insertUser(SystemRole.USER, "Owner");
		User creator = insertUser(SystemRole.USER, "Creator");
		User admin = insertUser(SystemRole.ADMIN, "Admin");
		Project project = insertProject(owner, ProjectStatus.IN_PROGRESS);
		addMember(project, creator);
		Task task = insertTask(project, owner);
		Project hiddenProject = insertProject(creator, ProjectStatus.IN_PROGRESS);
		Summary projectStage = insertSummary(project, null, owner, SummaryType.STAGE, "Project stage");
		Summary taskFinal = insertSummary(project, task, creator, SummaryType.FINAL, "Task final");
		Summary hidden = insertSummary(hiddenProject, null, creator, SummaryType.STAGE, "Hidden");
		LoginSession ownerSession = login(owner);

		assertListIds(ownerSession, "projectId=" + project.getId(), projectStage, taskFinal);
		assertListIds(ownerSession, "taskId=" + task.getId(), taskFinal);
		assertListIds(ownerSession, "taskId=none", projectStage);
		assertListIds(ownerSession, "type=FINAL", taskFinal);
		assertListIds(ownerSession, "createdBy=" + creator.getId(), taskFinal);
		assertListIds(ownerSession, "projectId=" + project.getId() + "&taskId=" + task.getId()
			+ "&type=FINAL&createdBy=" + creator.getId(), taskFinal);
		assertListIds(ownerSession, "projectId=" + hiddenProject.getId());
		assertListIds(loginUser(hiddenProject.getOwnerId()), "projectId=" + hiddenProject.getId(), hidden);
		assertListIds(ownerSession, "createdBy=" + creator.getId(), taskFinal);
		assertListIds(login(admin), "createdBy=" + creator.getId(), taskFinal, hidden);
	}

	@Test
	void clientCreatedByIsIgnoredInFavorOfTheAuthenticatedUser() throws Exception {
		User owner = insertUser(SystemRole.USER, "Owner");
		User attacker = insertUser(SystemRole.USER, "Requested creator");
		Project project = insertProject(owner, ProjectStatus.IN_PROGRESS);
		Map<String, Object> request = new LinkedHashMap<>();
		request.put("projectId", project.getId().toString());
		request.put("taskId", null);
		request.put("createdBy", attacker.getId().toString());
		request.put("type", SummaryType.STAGE);
		request.put("content", "Session identity wins");

		summaryRequest(post("/api/summaries"), login(owner), request)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.createdBy.id").value(owner.getId().toString()));
	}

	@Test
	void paginationSortingValidationAndStableTieBreakMatchTheContract() throws Exception {
		User owner = insertUser(SystemRole.USER, "Owner");
		Project project = insertProject(owner, ProjectStatus.IN_PROGRESS);
		Summary first = insertSummary(project, null, owner, SummaryType.STAGE, "First");
		Summary second = insertSummary(project, null, owner, SummaryType.STAGE, "Second");
		Summary third = insertSummary(project, null, owner, SummaryType.FINAL, "Third");
		LocalDateTime tied = LocalDateTime.of(2026, 7, 17, 12, 0);
		for (Summary summary : List.of(first, second, third)) {
			summaryMapper.update(null, Wrappers.<Summary>lambdaUpdate()
				.eq(Summary::getId, summary.getId())
				.set(Summary::getCreatedAt, tied)
				.set(Summary::getUpdatedAt, tied));
		}
		LoginSession session = login(owner);

		MvcResult page = summaryRequest(get("/api/summaries?projectId=" + project.getId()
			+ "&page=0&size=2"), session, null)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.page").value(0))
			.andExpect(jsonPath("$.size").value(2))
			.andExpect(jsonPath("$.totalElements").value(3))
			.andExpect(jsonPath("$.totalPages").value(2))
			.andReturn();
		assertThat(itemIds(page)).containsExactly(first.getId().toString(), second.getId().toString());
		MvcResult lastPage = summaryRequest(get("/api/summaries?projectId=" + project.getId()
			+ "&page=1&size=2"), session, null).andReturn();
		assertThat(itemIds(lastPage)).containsExactly(third.getId().toString());
		summaryRequest(get("/api/summaries?projectId=" + project.getId() + "&page=9&size=2"), session, null)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items").isEmpty());
		summaryRequest(get("/api/summaries?projectId=" + project.getId() + "&sort=type,asc"), session, null)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].type").value("FINAL"));

		for (String query : List.of("page=-1", "size=0", "size=101", "sort=content,asc",
			"sort=createdAt,sideways", "taskId=invalid", "type=UNKNOWN")) {
			assertProblem(summaryRequest(get("/api/summaries?" + query), session, null),
				422, "VALIDATION_ERROR");
		}
	}

	@Test
	void unrelatedDatabaseFailureIsNotReportedAsResourceInUse() throws Exception {
		User owner = insertUser(SystemRole.USER, "Owner");
		Project project = insertProject(owner, ProjectStatus.IN_PROGRESS);
		Summary summary = insertSummary(project, null, owner, SummaryType.STAGE, "Database failure");
		doThrow(new DataIntegrityViolationException("constraint chk_summaries_type"))
			.when(summaryMapper).deleteById(summary.getId());

		assertProblem(summaryRequest(delete("/api/summaries/" + summary.getId()), login(owner), null),
			500, "INTERNAL_SERVER_ERROR");
		assertThat(summaryMapper.selectById(summary.getId())).isNotNull();
	}

	@Test
	void outerTransactionFailuresRollBackInsertUpdateAndDelete() {
		User owner = insertUser(SystemRole.USER, "Owner");
		Project project = insertProject(owner, ProjectStatus.IN_PROGRESS);
		Summary summary = insertSummary(project, null, owner, SummaryType.STAGE, "Original");
		Authentication authentication = authentication(owner);
		TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
		long before = summaryMapper.selectCount(Wrappers.<Summary>lambdaQuery()
			.eq(Summary::getProjectId, project.getId()));

		assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
			summaryService.create(authentication, project.getId().toString(), null,
				SummaryType.STAGE, "Rolled back create");
			throw new IllegalStateException("rollback create");
		})).isInstanceOf(IllegalStateException.class);
		assertThat(summaryMapper.selectCount(Wrappers.<Summary>lambdaQuery()
			.eq(Summary::getProjectId, project.getId()))).isEqualTo(before);

		assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
			summaryService.update(authentication, summary.getId(), SummaryType.FINAL, "Rolled back update");
			throw new IllegalStateException("rollback update");
		})).isInstanceOf(IllegalStateException.class);
		assertThat(summaryMapper.selectById(summary.getId()).getContent()).isEqualTo("Original");

		assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
			summaryService.delete(authentication, summary.getId());
			throw new IllegalStateException("rollback delete");
		})).isInstanceOf(IllegalStateException.class);
		assertThat(summaryMapper.selectById(summary.getId())).isNotNull();
	}

	@Test
	void archiveWinningTheLockRacePreventsCreateAndUpdate() throws Exception {
		User owner = insertUser(SystemRole.USER, "Owner");
		Project project = insertProject(owner, ProjectStatus.IN_PROGRESS);
		Summary summary = insertSummary(project, null, owner, SummaryType.STAGE, "Before archive");
		Authentication authentication = authentication(owner);
		TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
		CountDownLatch archived = new CountDownLatch(1);
		CountDownLatch commitArchive = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(3);
		try {
			Future<?> archiver = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
				currentUserService.requireForUpdate(authentication);
				projectAccessService.requireProjectForUpdate(project.getId());
				projectMapper.update(null, Wrappers.<Project>lambdaUpdate()
					.eq(Project::getId, project.getId())
					.set(Project::getArchivedAt, LocalDateTime.now()));
				archived.countDown();
				await(commitArchive);
			}));
			assertThat(archived.await(5, TimeUnit.SECONDS)).isTrue();

			Future<?> create = executor.submit(() -> summaryService.create(
				authentication,
				project.getId().toString(),
				null,
				SummaryType.STAGE,
				"Racing create"));
			Future<?> update = executor.submit(() -> summaryService.update(
				authentication,
				summary.getId(),
				SummaryType.FINAL,
				"Racing update"));
			assertThatThrownBy(() -> create.get(500, TimeUnit.MILLISECONDS))
				.isInstanceOf(TimeoutException.class);
			assertThatThrownBy(() -> update.get(500, TimeUnit.MILLISECONDS))
				.isInstanceOf(TimeoutException.class);

			commitArchive.countDown();
			archiver.get(5, TimeUnit.SECONDS);
			assertProjectArchived(create);
			assertProjectArchived(update);
			assertThat(summaryMapper.selectCount(Wrappers.<Summary>lambdaQuery()
				.eq(Summary::getProjectId, project.getId()))).isOne();
			assertThat(summaryMapper.selectById(summary.getId()).getContent()).isEqualTo("Before archive");
		} finally {
			commitArchive.countDown();
			executor.shutdownNow();
			executor.awaitTermination(5, TimeUnit.SECONDS);
		}
	}

	@Test
	void concurrentCreatorUpdateAndOwnerDeleteAreSerialized() throws Exception {
		User owner = insertUser(SystemRole.USER, "Owner");
		User creator = insertUser(SystemRole.USER, "Creator");
		Project project = insertProject(owner, ProjectStatus.IN_PROGRESS);
		addMember(project, creator);
		Summary summary = insertSummary(project, null, creator, SummaryType.STAGE, "Original");
		TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
		CountDownLatch updated = new CountDownLatch(1);
		CountDownLatch commitUpdate = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<?> updater = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
				summaryAccessService.requireWritable(authentication(creator), summary.getId());
				summaryMapper.update(null, Wrappers.<Summary>lambdaUpdate()
					.eq(Summary::getId, summary.getId())
					.set(Summary::getType, SummaryType.FINAL)
					.set(Summary::getContent, "Updated"));
				updated.countDown();
				await(commitUpdate);
			}));
			assertThat(updated.await(5, TimeUnit.SECONDS)).isTrue();
			Future<?> deletion = executor.submit(() ->
				summaryService.delete(authentication(owner), summary.getId()));
			assertThatThrownBy(() -> deletion.get(500, TimeUnit.MILLISECONDS))
				.isInstanceOf(TimeoutException.class);

			commitUpdate.countDown();
			updater.get(5, TimeUnit.SECONDS);
			deletion.get(5, TimeUnit.SECONDS);
			assertThat(summaryMapper.selectById(summary.getId())).isNull();
		} finally {
			commitUpdate.countDown();
			executor.shutdownNow();
			executor.awaitTermination(5, TimeUnit.SECONDS);
		}
	}

	private ResultActions assertProblem(ResultActions result, int expectedStatus, String code)
		throws Exception {
		return result
			.andExpect(status().is(expectedStatus))
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(expectedStatus))
			.andExpect(jsonPath("$.code").value(code))
			.andExpect(jsonPath("$.errors").isArray());
	}

	private ResultActions summaryRequest(
		org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
		LoginSession session,
		Object body) throws Exception {
		request.session(session.session()).header(session.headerName(), session.token());
		if (body != null) {
			request.contentType(MediaType.APPLICATION_JSON);
			request.content(body instanceof String value
				? value
				: objectMapper.writeValueAsString(body));
		}
		return mockMvc.perform(request);
	}

	private void assertListIds(LoginSession session, String query, Summary... expected) throws Exception {
		MvcResult result = summaryRequest(get("/api/summaries?" + query), session, null)
			.andExpect(status().isOk())
			.andReturn();
		assertThat(itemIds(result)).containsExactlyInAnyOrder(
			java.util.Arrays.stream(expected).map(item -> item.getId().toString()).toArray(String[]::new));
	}

	private List<String> itemIds(MvcResult result) throws Exception {
		List<String> ids = new ArrayList<>();
		objectMapper.readTree(result.getResponse().getContentAsByteArray()).get("items")
			.forEach(item -> ids.add(item.get("id").asText()));
		return ids;
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

	private LoginSession loginUser(Long userId) throws Exception {
		return login(userMapper.selectById(userId));
	}

	private User insertUser(SystemRole role, String displayName) {
		User user = new User();
		user.setUsername("summary-http-" + UUID.randomUUID());
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
		project.setName("Summary HTTP " + UUID.randomUUID());
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

	private Task insertTask(Project project, User creator) {
		Task task = new Task();
		task.setProjectId(project.getId());
		task.setCreatorId(creator.getId());
		task.setTitle("Summary task");
		task.setStatus(TaskStatus.IN_PROGRESS);
		task.setPriority(Priority.MEDIUM);
		taskMapper.insert(task);
		return taskMapper.selectById(task.getId());
	}

	private Summary insertSummary(
		Project project,
		Task task,
		User creator,
		SummaryType type,
		String content) {
		Summary summary = new Summary();
		summary.setProjectId(project.getId());
		summary.setTaskId(task == null ? null : task.getId());
		summary.setCreatedBy(creator.getId());
		summary.setType(type);
		summary.setContent(content);
		summaryMapper.insert(summary);
		return summaryMapper.selectById(summary.getId());
	}

	private static CreateBody body(
		Project project,
		Task task,
		SummaryType type,
		String content) {
		return new CreateBody(
			project.getId().toString(),
			task == null ? null : task.getId().toString(),
			type,
			content);
	}

	private static Authentication authentication(User user) {
		return UsernamePasswordAuthenticationToken.authenticated(user.getUsername(), "", List.of());
	}

	private static void await(CountDownLatch latch) {
		try {
			if (!latch.await(5, TimeUnit.SECONDS)) {
				throw new AssertionError("Timed out waiting for concurrent summary operation");
			}
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new AssertionError(exception);
		}
	}

	private static void assertProjectArchived(Future<?> future) {
		assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS))
			.isInstanceOfSatisfying(ExecutionException.class, exception ->
				assertThat(exception.getCause())
					.isInstanceOfSatisfying(BusinessException.class, businessException ->
						assertThat(businessException.code()).isEqualTo(ErrorCode.PROJECT_ARCHIVED)));
	}

	record LoginBody(String username, String password) {
	}

	record LoginSession(MockHttpSession session, String headerName, String token) {
	}

	record CreateBody(String projectId, String taskId, SummaryType type, String content) {
	}

	record UpdateBody(SummaryType type, String content) {
	}
}
