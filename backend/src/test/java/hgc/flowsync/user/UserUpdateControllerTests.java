package hgc.flowsync.user;

import java.util.UUID;

import hgc.flowsync.project.InvitationStatus;
import hgc.flowsync.project.Priority;
import hgc.flowsync.project.Project;
import hgc.flowsync.project.ProjectInvitation;
import hgc.flowsync.project.ProjectInvitationMapper;
import hgc.flowsync.project.ProjectMapper;
import hgc.flowsync.project.ProjectMember;
import hgc.flowsync.project.ProjectMemberMapper;
import hgc.flowsync.project.ProjectStatus;
import hgc.flowsync.task.Task;
import hgc.flowsync.task.TaskMapper;
import hgc.flowsync.task.TaskStatus;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserUpdateControllerTests {

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private ObjectMapper objectMapper;
	@Autowired
	private UserMapper userMapper;
	@Autowired
	private PasswordEncoder passwordEncoder;
	@Autowired
	private ProjectMapper projectMapper;
	@Autowired
	private ProjectMemberMapper projectMemberMapper;
	@Autowired
	private ProjectInvitationMapper projectInvitationMapper;
	@Autowired
	private TaskMapper taskMapper;

	@AfterEach
	void deleteCommittedUsers() {
		if (!TestTransaction.isActive()) {
			userMapper.delete(Wrappers.<User>lambdaQuery().likeRight(User::getUsername, "update-"));
		}
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void adminUpdatesEveryFieldAndAccountChangesInvalidateTargetSessions() throws Exception {
		User admin = insertUser(SystemRole.ADMIN);
		User target = insertUser(SystemRole.USER);
		User deactivatedTarget = insertUser(SystemRole.USER);
		LoginSession adminSession = login(admin);
		LoginSession targetSession = login(target);
		LoginSession deactivatedSession = login(deactivatedTarget);

		update(adminSession, target, new UpdateUserBody(
			"Updated User",
			null,
			"updated@example.com",
			SystemRole.ADMIN,
			true))
			.andExpect(status().isOk())
			.andExpect(content().contentType(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.id").value(target.getId().toString()))
			.andExpect(jsonPath("$.username").value(target.getUsername()))
			.andExpect(jsonPath("$.displayName").value("Updated User"))
			.andExpect(jsonPath("$.phone").value((Object) null))
			.andExpect(jsonPath("$.email").value("updated@example.com"))
			.andExpect(jsonPath("$.systemRole").value("ADMIN"))
			.andExpect(jsonPath("$.active").value(true))
			.andExpect(jsonPath("$.passwordHash").doesNotExist());

		mockMvc.perform(get("/api/users/me").session(targetSession.session()))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
		update(adminSession, deactivatedTarget, body(deactivatedTarget, SystemRole.USER, false))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.active").value(false));
		mockMvc.perform(get("/api/users/me").session(deactivatedSession.session()))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
		mockMvc.perform(get("/api/users/me").session(adminSession.session()))
			.andExpect(status().isOk());
	}

	@Test
	void updateEnforcesProjectInvitationAndTaskConstraints() throws Exception {
		User admin = insertUser(SystemRole.ADMIN);
		User owner = insertUser(SystemRole.USER);
		User member = insertUser(SystemRole.USER);
		User invitee = insertUser(SystemRole.USER);
		User assignee = insertUser(SystemRole.USER);
		Project project = insertProject(owner);
		insertMember(project, member);
		insertMember(project, assignee);
		insertInvitation(project, owner, invitee);
		insertTask(project, owner, assignee);
		LoginSession adminSession = login(admin);

		update(adminSession, owner, body(owner, SystemRole.USER, false))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("USER_OWNS_PROJECT"));
		update(adminSession, assignee, body(assignee, SystemRole.USER, false))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("USER_HAS_ACTIVE_TASKS"));
		update(adminSession, member, body(member, SystemRole.ADMIN, true))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("USER_HAS_PROJECT_MEMBERSHIP"));
		update(adminSession, invitee, body(invitee, SystemRole.ADMIN, true))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("USER_HAS_PROJECT_MEMBERSHIP"));

		for (User user : new User[] {owner, member, invitee, assignee}) {
			User unchanged = userMapper.selectById(user.getId());
			assertThat(unchanged.getSystemRole()).isEqualTo(SystemRole.USER);
			assertThat(unchanged.isActive()).isTrue();
		}
	}

	@Test
	void updatePreservesTheLastActiveAdmin() throws Exception {
		User admin = insertUser(SystemRole.ADMIN);
		LoginSession adminSession = login(admin);
		userMapper.update(null, Wrappers.<User>lambdaUpdate()
			.eq(User::getSystemRole, SystemRole.ADMIN)
			.ne(User::getId, admin.getId())
			.set(User::isActive, false));

		update(adminSession, admin, body(admin, SystemRole.USER, true))
			.andExpect(status().isConflict())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("LAST_ADMIN_REQUIRED"));

		User unchanged = userMapper.selectById(admin.getId());
		assertThat(unchanged.getSystemRole()).isEqualTo(SystemRole.ADMIN);
		assertThat(unchanged.isActive()).isTrue();
	}

	@Test
	void updateRequiresAdminCompleteBodyAndExistingTarget() throws Exception {
		User admin = insertUser(SystemRole.ADMIN);
		User ordinaryUser = insertUser(SystemRole.USER);
		LoginSession adminSession = login(admin);
		LoginSession userSession = login(ordinaryUser);

		update(userSession, admin, body(admin, SystemRole.ADMIN, true))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));
		mockMvc.perform(put("/api/users/{userId}", ordinaryUser.getId())
				.session(adminSession.session())
				.header(adminSession.headerName(), adminSession.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"displayName":"Missing Active","phone":null,"email":null,"systemRole":"USER"}
					"""))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
			.andExpect(jsonPath("$.errors[0].field").value("active"));
		mockMvc.perform(put("/api/users/{userId}", Long.MAX_VALUE)
				.session(adminSession.session())
				.header(adminSession.headerName(), adminSession.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsBytes(body(ordinaryUser, SystemRole.USER, true))))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOT_FOUND"));
		update(adminSession, ordinaryUser, new UpdateUserBody(
			ordinaryUser.getDisplayName(), "", null, SystemRole.USER, true))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.errors[0].field").value("phone"));
	}

	private LoginSession login(User user) throws Exception {
		MvcResult csrfResult = mockMvc.perform(get("/api/auth/csrf")).andReturn();
		JsonNode csrf = objectMapper.readTree(csrfResult.getResponse().getContentAsByteArray());
		MockHttpSession session = (MockHttpSession) csrfResult.getRequest().getSession(false);
		mockMvc.perform(post("/api/auth/login")
				.session(session)
				.header(csrf.get("headerName").asText(), csrf.get("token").asText())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsBytes(new LoginBody(user.getUsername(), "test-password"))))
			.andExpect(status().isOk());
		return new LoginSession(
			session,
			csrf.get("headerName").asText(),
			csrf.get("token").asText());
	}

	private org.springframework.test.web.servlet.ResultActions update(
		LoginSession session,
		User target,
		UpdateUserBody body) throws Exception {
		return mockMvc.perform(put("/api/users/{userId}", target.getId())
			.session(session.session())
			.header(session.headerName(), session.token())
			.contentType(MediaType.APPLICATION_JSON)
			.content(objectMapper.writeValueAsBytes(body)));
	}

	private User insertUser(SystemRole role) {
		User user = new User();
		user.setUsername("update-" + UUID.randomUUID());
		user.setPasswordHash(passwordEncoder.encode("test-password"));
		user.setDisplayName("Update User");
		user.setSystemRole(role);
		userMapper.insert(user);
		return userMapper.selectById(user.getId());
	}

	private Project insertProject(User owner) {
		Project project = new Project();
		project.setOwnerId(owner.getId());
		project.setName("Update Test Project");
		project.setStatus(ProjectStatus.NOT_STARTED);
		project.setPriority(Priority.MEDIUM);
		projectMapper.insert(project);
		insertMember(project, owner);
		return project;
	}

	private void insertMember(Project project, User user) {
		ProjectMember member = new ProjectMember();
		member.setProjectId(project.getId());
		member.setUserId(user.getId());
		projectMemberMapper.insert(member);
	}

	private void insertInvitation(Project project, User owner, User invitee) {
		ProjectInvitation invitation = new ProjectInvitation();
		invitation.setProjectId(project.getId());
		invitation.setInviteeId(invitee.getId());
		invitation.setInvitedBy(owner.getId());
		invitation.setStatus(InvitationStatus.PENDING);
		projectInvitationMapper.insert(invitation);
	}

	private void insertTask(Project project, User creator, User assignee) {
		Task task = new Task();
		task.setProjectId(project.getId());
		task.setCreatorId(creator.getId());
		task.setAssigneeId(assignee.getId());
		task.setTitle("Incomplete Task");
		task.setStatus(TaskStatus.IN_PROGRESS);
		task.setPriority(Priority.MEDIUM);
		taskMapper.insert(task);
	}

	private static UpdateUserBody body(User user, SystemRole role, boolean active) {
		return new UpdateUserBody(user.getDisplayName(), user.getPhone(), user.getEmail(), role, active);
	}

	record LoginBody(String username, String password) {
	}

	record UpdateUserBody(
		String displayName,
		String phone,
		String email,
		SystemRole systemRole,
		Boolean active) {
	}

	record LoginSession(MockHttpSession session, String headerName, String token) {
	}
}
