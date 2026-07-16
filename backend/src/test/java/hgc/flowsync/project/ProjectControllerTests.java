package hgc.flowsync.project;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import hgc.flowsync.user.SystemRole;
import hgc.flowsync.user.User;
import hgc.flowsync.user.UserMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProjectControllerTests {

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private ObjectMapper objectMapper;
	@Autowired
	private UserMapper userMapper;
	@Autowired
	private ProjectMapper projectMapper;
	@MockitoSpyBean
	private ProjectMemberMapper projectMemberMapper;
	@Autowired
	private PasswordEncoder passwordEncoder;

	private final List<String> usernames = new ArrayList<>();
	private final List<String> projectNames = new ArrayList<>();

	@AfterEach
	void deleteCreatedRecords() {
		List<Long> projectIds = projectMapper.selectList(Wrappers.<Project>lambdaQuery()
			.in(Project::getName, projectNames)).stream().map(Project::getId).toList();
		if (!projectIds.isEmpty()) {
			projectMemberMapper.delete(Wrappers.<ProjectMember>lambdaQuery()
				.in(ProjectMember::getProjectId, projectIds));
			projectMapper.delete(Wrappers.<Project>lambdaQuery().in(Project::getId, projectIds));
		}
		userMapper.delete(Wrappers.<User>lambdaQuery().in(User::getUsername, usernames));
	}

	@Test
	void userCreatesProjectAndBecomesOwnerMember() throws Exception {
		User user = insertUser(SystemRole.USER, true);
		LoginSession session = login(user);
		String projectName = projectName();

		create(session, new CreateProjectBody(
			projectName,
			"Course project",
			ProjectStatus.NOT_STARTED,
			Priority.HIGH,
			LocalDate.of(2026, 7, 1),
			LocalDate.of(2026, 8, 31),
			null))
			.andExpect(status().isCreated())
			.andExpect(content().contentType(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.owner.id").value(user.getId().toString()))
			.andExpect(jsonPath("$.owner.displayName").value(user.getDisplayName()))
			.andExpect(jsonPath("$.name").value(projectName))
			.andExpect(jsonPath("$.description").value("Course project"))
			.andExpect(jsonPath("$.status").value("NOT_STARTED"))
			.andExpect(jsonPath("$.priority").value("HIGH"))
			.andExpect(jsonPath("$.startDate").value("2026-07-01"))
			.andExpect(jsonPath("$.endDate").value("2026-08-31"))
			.andExpect(jsonPath("$.archivedAt").value((Object) null))
			.andExpect(jsonPath("$.memberCount").value(1))
			.andExpect(jsonPath("$.taskStats.total").value(0))
			.andExpect(jsonPath("$.taskStats.completed").value(0))
			.andExpect(jsonPath("$.createdAt").isNotEmpty())
			.andExpect(jsonPath("$.updatedAt").isNotEmpty());

		Project project = projectMapper.selectOne(Wrappers.<Project>lambdaQuery()
			.eq(Project::getName, projectName));
		assertThat(project.getOwnerId()).isEqualTo(user.getId());
		assertThat(projectMemberMapper.selectCount(Wrappers.<ProjectMember>lambdaQuery()
			.eq(ProjectMember::getProjectId, project.getId())
			.eq(ProjectMember::getUserId, user.getId()))).isOne();
	}

	@Test
	void adminCreatesProjectForAnActiveUserWithoutJoiningIt() throws Exception {
		User admin = insertUser(SystemRole.ADMIN, true);
		User owner = insertUser(SystemRole.USER, true);
		LoginSession session = login(admin);
		String projectName = projectName();

		create(session, body(projectName, owner.getId().toString()))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.owner.id").value(owner.getId().toString()))
			.andExpect(jsonPath("$.memberCount").value(1));

		Project project = projectMapper.selectOne(Wrappers.<Project>lambdaQuery()
			.eq(Project::getName, projectName));
		assertThat(projectMemberMapper.selectCount(Wrappers.<ProjectMember>lambdaQuery()
			.eq(ProjectMember::getProjectId, project.getId())
			.eq(ProjectMember::getUserId, owner.getId()))).isOne();
		assertThat(projectMemberMapper.selectCount(Wrappers.<ProjectMember>lambdaQuery()
			.eq(ProjectMember::getProjectId, project.getId())
			.eq(ProjectMember::getUserId, admin.getId()))).isZero();
	}

	@Test
	void createValidatesOwnerRulesCompleteBodyAndDateRange() throws Exception {
		User admin = insertUser(SystemRole.ADMIN, true);
		User user = insertUser(SystemRole.USER, true);
		User inactiveUser = insertUser(SystemRole.USER, false);
		LoginSession adminSession = login(admin);
		LoginSession userSession = login(user);

		create(userSession, body(projectName(), inactiveUser.getId().toString()))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		create(adminSession, body(projectName(), null))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		create(adminSession, body(projectName(), inactiveUser.getId().toString()))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		create(adminSession, body(projectName(), admin.getId().toString()))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		create(adminSession, body(projectName(), Long.toString(Long.MAX_VALUE)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOT_FOUND"));
		create(userSession, new CreateProjectBody(
			projectName(),
			null,
			ProjectStatus.NOT_STARTED,
			Priority.MEDIUM,
			LocalDate.of(2026, 8, 1),
			LocalDate.of(2026, 7, 31),
			null))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		mockMvc.perform(post("/api/projects")
				.session(userSession.session())
				.header(userSession.headerName(), userSession.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"name":"Missing Description","status":"NOT_STARTED","priority":"MEDIUM",
					 "startDate":null,"endDate":null,"ownerId":null}
					"""))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
			.andExpect(jsonPath("$.errors[0].field").value("description"));
	}

	@Test
	void memberInsertFailureRollsBackProject() throws Exception {
		User user = insertUser(SystemRole.USER, true);
		LoginSession session = login(user);
		String projectName = projectName();
		doThrow(new DataAccessResourceFailureException("forced member failure"))
			.when(projectMemberMapper).insert(any(ProjectMember.class));

		create(session, body(projectName, null))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));

		assertThat(projectMapper.selectCount(Wrappers.<Project>lambdaQuery()
			.eq(Project::getName, projectName))).isZero();
	}

	private ResultActions create(LoginSession session, CreateProjectBody body) throws Exception {
		return mockMvc.perform(post("/api/projects")
			.session(session.session())
			.header(session.headerName(), session.token())
			.contentType(MediaType.APPLICATION_JSON)
			.content(objectMapper.writeValueAsBytes(body)));
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
		return new LoginSession(
			session,
			csrf.get("headerName").asText(),
			csrf.get("token").asText());
	}

	private User insertUser(SystemRole role, boolean active) {
		String username = "project-" + UUID.randomUUID();
		usernames.add(username);
		User user = new User();
		user.setUsername(username);
		user.setPasswordHash(passwordEncoder.encode("test-password"));
		user.setDisplayName("Project User");
		user.setSystemRole(role);
		user.setActive(active);
		userMapper.insert(user);
		return userMapper.selectById(user.getId());
	}

	private String projectName() {
		String name = "Project " + UUID.randomUUID();
		projectNames.add(name);
		return name;
	}

	private static CreateProjectBody body(String name, String ownerId) {
		return new CreateProjectBody(
			name,
			null,
			ProjectStatus.NOT_STARTED,
			Priority.MEDIUM,
			null,
			null,
			ownerId);
	}

	record LoginBody(String username, String password) {
	}

	record CreateProjectBody(
		String name,
		String description,
		ProjectStatus status,
		Priority priority,
		LocalDate startDate,
		LocalDate endDate,
		String ownerId) {
	}

	record LoginSession(MockHttpSession session, String headerName, String token) {
	}
}
