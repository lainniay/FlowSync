package hgc.flowsync.project;

import java.util.List;
import java.util.UUID;

import hgc.flowsync.user.SystemRole;
import hgc.flowsync.user.User;
import hgc.flowsync.user.UserMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProjectMembershipControllerTests {

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
	private PasswordEncoder passwordEncoder;

	@Test
	void adminAddsMembersAndCancelsTheirPendingInvitations() throws Exception {
		User owner = insertUser(SystemRole.USER, true);
		User admin = insertUser(SystemRole.ADMIN, true);
		User first = insertUser(SystemRole.USER, true);
		User second = insertUser(SystemRole.USER, true);
		Project project = insertProject(owner);
		ProjectInvitation pending = insertInvitation(project, first, owner, InvitationStatus.PENDING);
		LoginSession adminSession = login(admin);

		postIds(adminSession, "/api/projects/" + project.getId() + "/members", first, second)
			.andExpect(status().isCreated())
			.andExpect(content().contentType(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$[0].user.id").value(first.getId().toString()))
			.andExpect(jsonPath("$[0].joinedAt").isNotEmpty())
			.andExpect(jsonPath("$[1].user.id").value(second.getId().toString()));

		ProjectInvitation cancelled = projectInvitationMapper.selectById(pending.getId());
		assertThat(cancelled.getStatus()).isEqualTo(InvitationStatus.CANCELLED);
		assertThat(cancelled.getRespondedAt()).isNotNull();
		getMembers(login(owner), project)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(3));
	}

	@Test
	void directMemberBatchValidatesEverythingBeforeWriting() throws Exception {
		User owner = insertUser(SystemRole.USER, true);
		User admin = insertUser(SystemRole.ADMIN, true);
		User valid = insertUser(SystemRole.USER, true);
		User inactive = insertUser(SystemRole.USER, false);
		Project project = insertProject(owner);

		postIds(login(owner), "/api/projects/" + project.getId() + "/members", valid)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));
		postIds(login(admin), "/api/projects/" + project.getId() + "/members", valid, inactive)
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		postIds(login(admin), "/api/projects/" + project.getId() + "/members", valid, valid)
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		getMembers(login(valid), project)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));

		assertThat(projectMemberMapper.existsByProjectIdAndUserId(project.getId(), valid.getId())).isFalse();
	}

	@Test
	void ownerManagesInvitationsAndInviteeAcceptsReinvitation() throws Exception {
		User owner = insertUser(SystemRole.USER, true);
		User admin = insertUser(SystemRole.ADMIN, true);
		User invitee = insertUser(SystemRole.USER, true);
		User outsider = insertUser(SystemRole.USER, true);
		Project project = insertProject(owner);
		LoginSession ownerSession = login(owner);

		MvcResult created = postIds(
			ownerSession,
			"/api/projects/" + project.getId() + "/invitations",
			invitee)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$[0].project.id").value(project.getId().toString()))
			.andExpect(jsonPath("$[0].invitee.id").value(invitee.getId().toString()))
			.andExpect(jsonPath("$[0].invitedBy.id").value(owner.getId().toString()))
			.andExpect(jsonPath("$[0].status").value("PENDING"))
			.andExpect(jsonPath("$[0].createdAt").isNotEmpty())
			.andExpect(jsonPath("$[0].respondedAt").value((Object) null))
			.andReturn();
		String invitationId = objectMapper.readTree(created.getResponse().getContentAsByteArray())
			.get(0).get("id").asText();

		postIds(ownerSession, "/api/projects/" + project.getId() + "/invitations", invitee)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INVITATION_ALREADY_PENDING"));
		postIds(login(admin), "/api/projects/" + project.getId() + "/invitations", outsider)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));
		mockMvc.perform(get("/api/projects/{projectId}/invitations", project.getId())
				.session(login(admin).session()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].id").value(invitationId));

		respond(login(outsider), invitationId, InvitationStatus.ACCEPTED)
			.andExpect(status().isForbidden());
		respond(login(invitee), invitationId, InvitationStatus.CANCELLED)
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		respond(login(invitee), invitationId, InvitationStatus.REJECTED)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("REJECTED"))
			.andExpect(jsonPath("$.respondedAt").isNotEmpty());

		postIds(ownerSession, "/api/projects/" + project.getId() + "/invitations", invitee)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$[0].id").value(invitationId))
			.andExpect(jsonPath("$[0].status").value("PENDING"))
			.andExpect(jsonPath("$[0].respondedAt").value((Object) null));
		mockMvc.perform(get("/api/project-invitations")
				.param("status", "PENDING")
				.session(login(invitee).session()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].id").value(invitationId));
		respond(login(invitee), invitationId, InvitationStatus.ACCEPTED)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("ACCEPTED"));

		assertThat(projectMemberMapper.existsByProjectIdAndUserId(project.getId(), invitee.getId())).isTrue();
		respond(login(invitee), invitationId, InvitationStatus.ACCEPTED)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INVALID_INVITATION_STATE"));
	}

	@Test
	void invitationCancellationAndArchivedProjectRulesAreEnforced() throws Exception {
		User owner = insertUser(SystemRole.USER, true);
		User admin = insertUser(SystemRole.ADMIN, true);
		User invitee = insertUser(SystemRole.USER, true);
		User another = insertUser(SystemRole.USER, true);
		Project project = insertProject(owner);
		ProjectInvitation pending = insertInvitation(project, invitee, owner, InvitationStatus.PENDING);

		LoginSession adminSession = login(admin);
		mockMvc.perform(delete("/api/projects/{projectId}/invitations/{invitationId}",
				project.getId(), pending.getId())
				.session(adminSession.session())
				.header(adminSession.headerName(), adminSession.token()))
			.andExpect(status().isNoContent());
		assertThat(projectInvitationMapper.selectById(pending.getId()).getStatus())
			.isEqualTo(InvitationStatus.CANCELLED);

		ProjectInvitation accepting = insertInvitation(project, another, owner, InvitationStatus.PENDING);
		project.setArchivedAt(java.time.LocalDateTime.now());
		projectMapper.updateById(project);
		postIds(adminSession, "/api/projects/" + project.getId() + "/members", invitee)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("PROJECT_ARCHIVED"));
		postIds(login(owner), "/api/projects/" + project.getId() + "/invitations", invitee)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("PROJECT_ARCHIVED"));
		respond(login(another), accepting.getId().toString(), InvitationStatus.ACCEPTED)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("PROJECT_ARCHIVED"));
	}

	@Test
	void memberAndInvitationWritesRequireAuthenticationAndCsrf() throws Exception {
		User owner = insertUser(SystemRole.USER, true);
		User invitee = insertUser(SystemRole.USER, true);
		Project project = insertProject(owner);

		mockMvc.perform(get("/api/projects/{projectId}/members", project.getId()))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
		mockMvc.perform(post("/api/projects/{projectId}/invitations", project.getId())
				.session(login(owner).session())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsBytes(new UserIdsBody(List.of(invitee.getId().toString())))))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("CSRF_INVALID"));
	}

	private ResultActions postIds(LoginSession session, String path, User... users) throws Exception {
		return mockMvc.perform(post(path)
			.session(session.session())
			.header(session.headerName(), session.token())
			.contentType(MediaType.APPLICATION_JSON)
			.content(objectMapper.writeValueAsBytes(new UserIdsBody(
				java.util.Arrays.stream(users).map(user -> user.getId().toString()).toList()))));
	}

	private ResultActions getMembers(LoginSession session, Project project) throws Exception {
		return mockMvc.perform(get("/api/projects/{projectId}/members", project.getId())
			.session(session.session()));
	}

	private ResultActions respond(LoginSession session, String invitationId, InvitationStatus status) throws Exception {
		return mockMvc.perform(put("/api/project-invitations/{invitationId}", invitationId)
			.session(session.session())
			.header(session.headerName(), session.token())
			.contentType(MediaType.APPLICATION_JSON)
			.content(objectMapper.writeValueAsBytes(new RespondBody(status))));
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

	private User insertUser(SystemRole role, boolean active) {
		User user = new User();
		user.setUsername("membership-" + UUID.randomUUID());
		user.setPasswordHash(passwordEncoder.encode("test-password"));
		user.setDisplayName("Membership User");
		user.setSystemRole(role);
		user.setActive(active);
		userMapper.insert(user);
		return userMapper.selectById(user.getId());
	}

	private Project insertProject(User owner) {
		Project project = new Project();
		project.setOwnerId(owner.getId());
		project.setName("Membership " + UUID.randomUUID());
		project.setStatus(ProjectStatus.NOT_STARTED);
		project.setPriority(Priority.MEDIUM);
		projectMapper.insert(project);
		ProjectMember member = new ProjectMember();
		member.setProjectId(project.getId());
		member.setUserId(owner.getId());
		projectMemberMapper.insert(member);
		return projectMapper.selectById(project.getId());
	}

	private ProjectInvitation insertInvitation(
		Project project,
		User invitee,
		User inviter,
		InvitationStatus status) {
		ProjectInvitation invitation = new ProjectInvitation();
		invitation.setProjectId(project.getId());
		invitation.setInviteeId(invitee.getId());
		invitation.setInvitedBy(inviter.getId());
		invitation.setStatus(status);
		projectInvitationMapper.insert(invitation);
		return projectInvitationMapper.selectById(invitation.getId());
	}

	record LoginBody(String username, String password) {
	}

	record UserIdsBody(List<String> userIds) {
	}

	record RespondBody(InvitationStatus status) {
	}

	record LoginSession(MockHttpSession session, String headerName, String token) {
	}
}
