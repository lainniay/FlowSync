package hgc.flowsync.auth;

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
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

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
class AuthControllerTests {

	private final MockMvc mockMvc;
	private final ObjectMapper objectMapper;
	private final UserMapper userMapper;
	private final PasswordEncoder passwordEncoder;

	@Autowired
	AuthControllerTests(
		MockMvc mockMvc,
		ObjectMapper objectMapper,
		UserMapper userMapper,
		PasswordEncoder passwordEncoder) {
		this.mockMvc = mockMvc;
		this.objectMapper = objectMapper;
		this.userMapper = userMapper;
		this.passwordEncoder = passwordEncoder;
	}

	@Test
	void csrfLoginCurrentUserAndLogoutUseSession() throws Exception {
		User user = insertUser(true);
		MvcResult csrfResult = mockMvc.perform(get("/api/auth/csrf"))
			.andExpect(status().isOk())
			.andExpect(content().contentType(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.token").isNotEmpty())
			.andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
			.andReturn();
		MockHttpSession session = (MockHttpSession) csrfResult.getRequest().getSession(false);
		JsonNode csrf = objectMapper.readTree(csrfResult.getResponse().getContentAsByteArray());
		String sessionIdBeforeLogin = session.getId();

		mockMvc.perform(post("/api/auth/login")
				.session(session)
				.header(csrf.get("headerName").asText(), csrf.get("token").asText())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsBytes(new LoginBody(user.getUsername(), "test-password"))))
			.andExpect(status().isOk())
			.andExpect(content().contentType(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.id").value(user.getId().toString()))
			.andExpect(jsonPath("$.username").value(user.getUsername()))
			.andExpect(jsonPath("$.systemRole").value("USER"))
			.andExpect(jsonPath("$.active").value(true))
			.andExpect(jsonPath("$.createdAt").isNotEmpty())
			.andExpect(jsonPath("$.updatedAt").isNotEmpty())
			.andExpect(jsonPath("$.passwordHash").doesNotExist());

		assertThat(session.getId()).isNotEqualTo(sessionIdBeforeLogin);
		assertThat(session.getAttribute(
			HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)).isNotNull();

		mockMvc.perform(get("/api/users/me").session(session))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.username").value(user.getUsername()));

		mockMvc.perform(post("/api/auth/logout")
				.session(session)
				.header(csrf.get("headerName").asText(), csrf.get("token").asText()))
			.andExpect(status().isNoContent())
			.andExpect(content().string(""));
		assertThat(session.isInvalid()).isTrue();
	}

	@Test
	void invalidCredentialsReturnProblemDetails() throws Exception {
		User user = insertUser(true);
		MvcResult csrfResult = mockMvc.perform(get("/api/auth/csrf")).andReturn();
		MockHttpSession session = (MockHttpSession) csrfResult.getRequest().getSession(false);
		JsonNode csrf = objectMapper.readTree(csrfResult.getResponse().getContentAsByteArray());

		mockMvc.perform(post("/api/auth/login")
				.session(session)
				.header(csrf.get("headerName").asText(), csrf.get("token").asText())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsBytes(new LoginBody(user.getUsername(), "wrong-password"))))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
			.andExpect(jsonPath("$.detail").value("Username or password is incorrect."))
			.andExpect(jsonPath("$.errors").isEmpty());
	}

	@Test
	void loginRejectsPasswordBeyondBcryptByteLimit() throws Exception {
		User user = insertUser(true);
		user.setPasswordHash(passwordEncoder.encode("a".repeat(72)));
		userMapper.updateById(user);
		LoginSession loginSession = newSession();

		login(loginSession, user.getUsername(), "a".repeat(72) + "suffix")
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
	}

	@Test
	void inactiveUserReturnsInvalidCredentials() throws Exception {
		User user = insertUser(false);
		MvcResult csrfResult = mockMvc.perform(get("/api/auth/csrf")).andReturn();
		MockHttpSession session = (MockHttpSession) csrfResult.getRequest().getSession(false);
		JsonNode csrf = objectMapper.readTree(csrfResult.getResponse().getContentAsByteArray());

		mockMvc.perform(post("/api/auth/login")
				.session(session)
				.header(csrf.get("headerName").asText(), csrf.get("token").asText())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsBytes(new LoginBody(user.getUsername(), "test-password"))))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
			.andExpect(jsonPath("$.detail").value("Username or password is incorrect."))
			.andExpect(jsonPath("$.errors").isEmpty());
	}

	@Test
	void userUpdatesProfileWithoutChangingAccountFields() throws Exception {
		User user = insertUser(true);
		String originalPasswordHash = user.getPasswordHash();
		LoginSession loginSession = newSession();
		login(loginSession, user.getUsername(), "test-password").andExpect(status().isOk());

		updateProfile(loginSession, new UpdateProfileBody(
			"Updated Name",
			"13800138000",
			"updated@example.com"))
			.andExpect(status().isOk())
			.andExpect(content().contentType(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.id").value(user.getId().toString()))
			.andExpect(jsonPath("$.username").value(user.getUsername()))
			.andExpect(jsonPath("$.displayName").value("Updated Name"))
			.andExpect(jsonPath("$.phone").value("13800138000"))
			.andExpect(jsonPath("$.email").value("updated@example.com"))
			.andExpect(jsonPath("$.systemRole").value("USER"))
			.andExpect(jsonPath("$.active").value(true))
			.andExpect(jsonPath("$.passwordHash").doesNotExist());

		updateProfile(loginSession, new UpdateProfileBody("Updated Again", null, null))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.displayName").value("Updated Again"))
			.andExpect(jsonPath("$.phone").value((Object) null))
			.andExpect(jsonPath("$.email").value((Object) null));

		User updated = userMapper.selectById(user.getId());
		assertThat(updated.getUsername()).isEqualTo(user.getUsername());
		assertThat(updated.getPasswordHash()).isEqualTo(originalPasswordHash);
		assertThat(updated.getSystemRole()).isEqualTo(SystemRole.USER);
		assertThat(updated.isActive()).isTrue();
		assertThat(updated.getDisplayName()).isEqualTo("Updated Again");
		assertThat(updated.getPhone()).isNull();
		assertThat(updated.getEmail()).isNull();
	}

	@Test
	void updateProfileRequiresNullableFieldsAndValidatesValues() throws Exception {
		User user = insertUser(true);
		LoginSession loginSession = newSession();
		login(loginSession, user.getUsername(), "test-password").andExpect(status().isOk());

		mockMvc.perform(put("/api/users/me")
				.session(loginSession.session())
				.header(loginSession.headerName(), loginSession.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"displayName":"Missing Phone","email":null}
					"""))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
			.andExpect(jsonPath("$.errors[0].field").value("phone"));

		updateProfile(loginSession, new UpdateProfileBody("Invalid Email", null, "not-an-email"))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
			.andExpect(jsonPath("$.errors[0].field").value("email"));

		assertThat(userMapper.selectById(user.getId()).getDisplayName()).isEqualTo("Session Test");
	}

	@Test
	void changingPasswordInvalidatesEverySessionAndReplacesCredentials() throws Exception {
		User user = insertUser(true);
		LoginSession first = newSession();
		LoginSession second = newSession();
		login(first, user.getUsername(), "test-password").andExpect(status().isOk());
		login(second, user.getUsername(), "test-password").andExpect(status().isOk());

		mockMvc.perform(put("/api/users/me/password")
				.session(first.session())
				.header(first.headerName(), first.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsBytes(
					new ChangePasswordBody("test-password", "new-test-password"))))
			.andExpect(status().isNoContent())
			.andExpect(content().string(""));

		for (LoginSession loginSession : new LoginSession[] {first, second}) {
			mockMvc.perform(get("/api/users/me").session(loginSession.session()))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
		}

		LoginSession oldPassword = newSession();
		login(oldPassword, user.getUsername(), "test-password")
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
		login(newSession(), user.getUsername(), "new-test-password")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.username").value(user.getUsername()));
	}

	@Test
	void incorrectCurrentPasswordDoesNotChangePasswordOrSession() throws Exception {
		User user = insertUser(true);
		LoginSession loginSession = newSession();
		login(loginSession, user.getUsername(), "test-password").andExpect(status().isOk());

		mockMvc.perform(put("/api/users/me/password")
				.session(loginSession.session())
				.header(loginSession.headerName(), loginSession.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsBytes(
					new ChangePasswordBody("wrong-password", "new-test-password"))))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_INCORRECT"))
			.andExpect(jsonPath("$.errors").isEmpty());

		mockMvc.perform(get("/api/users/me").session(loginSession.session()))
			.andExpect(status().isOk());
		assertThat(passwordEncoder.matches(
			"test-password",
			userMapper.selectById(user.getId()).getPasswordHash())).isTrue();
	}

	@Test
	void currentPasswordBeyondBcryptByteLimitIsIncorrect() throws Exception {
		String password = "密".repeat(24);
		User user = insertUser(true);
		user.setPasswordHash(passwordEncoder.encode(password));
		userMapper.updateById(user);
		LoginSession loginSession = newSession();
		login(loginSession, user.getUsername(), password).andExpect(status().isOk());

		mockMvc.perform(put("/api/users/me/password")
				.session(loginSession.session())
				.header(loginSession.headerName(), loginSession.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsBytes(
					new ChangePasswordBody(password + "suffix", "new-test-password"))))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_INCORRECT"));

		mockMvc.perform(get("/api/users/me").session(loginSession.session()))
			.andExpect(status().isOk());
		assertThat(passwordEncoder.matches(
			password,
			userMapper.selectById(user.getId()).getPasswordHash())).isTrue();
	}

	@Test
	void passwordOverBcryptByteLimitReturnsValidationError() throws Exception {
		User user = insertUser(true);
		LoginSession loginSession = newSession();
		login(loginSession, user.getUsername(), "test-password").andExpect(status().isOk());

		mockMvc.perform(put("/api/users/me/password")
				.session(loginSession.session())
				.header(loginSession.headerName(), loginSession.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsBytes(
					new ChangePasswordBody("test-password", "密".repeat(25)))))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
			.andExpect(jsonPath("$.errors[0].field").value("newPassword"));
	}

	@Test
	void adminResetReplacesCredentialsAndInvalidatesTargetSessions() throws Exception {
		User admin = insertUser(true, SystemRole.ADMIN);
		User target = insertUser(true);
		LoginSession adminSession = newSession();
		LoginSession targetFirst = newSession();
		LoginSession targetSecond = newSession();
		login(adminSession, admin.getUsername(), "test-password").andExpect(status().isOk());
		login(targetFirst, target.getUsername(), "test-password").andExpect(status().isOk());
		login(targetSecond, target.getUsername(), "test-password").andExpect(status().isOk());

		mockMvc.perform(put("/api/users/{userId}/password", target.getId())
				.session(adminSession.session())
				.header(adminSession.headerName(), adminSession.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsBytes(new ResetPasswordBody("reset-test-password"))))
			.andExpect(status().isNoContent())
			.andExpect(content().string(""));

		for (LoginSession targetSession : new LoginSession[] {targetFirst, targetSecond}) {
			mockMvc.perform(get("/api/users/me").session(targetSession.session()))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
		}
		mockMvc.perform(get("/api/users/me").session(adminSession.session()))
			.andExpect(status().isOk());

		LoginSession oldPassword = newSession();
		login(oldPassword, target.getUsername(), "test-password")
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
		login(newSession(), target.getUsername(), "reset-test-password")
			.andExpect(status().isOk());
	}

	@Test
	void nonAdminCannotResetPassword() throws Exception {
		User actor = insertUser(true);
		User target = insertUser(true);
		LoginSession actorSession = newSession();
		login(actorSession, actor.getUsername(), "test-password").andExpect(status().isOk());

		mockMvc.perform(put("/api/users/{userId}/password", target.getId())
				.session(actorSession.session())
				.header(actorSession.headerName(), actorSession.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsBytes(new ResetPasswordBody("reset-test-password"))))
			.andExpect(status().isForbidden())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));

		assertThat(passwordEncoder.matches(
			"test-password",
			userMapper.selectById(target.getId()).getPasswordHash())).isTrue();
	}

	@Test
	void adminResetValidatesTargetAndPassword() throws Exception {
		User admin = insertUser(true, SystemRole.ADMIN);
		User target = insertUser(true);
		LoginSession adminSession = newSession();
		login(adminSession, admin.getUsername(), "test-password").andExpect(status().isOk());

		mockMvc.perform(put("/api/users/{userId}/password", Long.MAX_VALUE)
				.session(adminSession.session())
				.header(adminSession.headerName(), adminSession.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsBytes(new ResetPasswordBody("reset-test-password"))))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOT_FOUND"));

		mockMvc.perform(put("/api/users/{userId}/password", target.getId())
				.session(adminSession.session())
				.header(adminSession.headerName(), adminSession.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsBytes(new ResetPasswordBody("short"))))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
			.andExpect(jsonPath("$.errors[0].field").value("newPassword"));
	}

	@Test
	void adminCreatesUserWithEncryptedPassword() throws Exception {
		User admin = insertUser(true, SystemRole.ADMIN);
		LoginSession adminSession = newSession();
		login(adminSession, admin.getUsername(), "test-password").andExpect(status().isOk());
		String username = "created-" + UUID.randomUUID();

		createUser(adminSession, new CreateUserBody(
			username,
			"initial-test-password",
			"Created User",
			SystemRole.USER,
			null,
			"created@example.com"))
			.andExpect(status().isCreated())
			.andExpect(content().contentType(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.username").value(username))
			.andExpect(jsonPath("$.displayName").value("Created User"))
			.andExpect(jsonPath("$.systemRole").value("USER"))
			.andExpect(jsonPath("$.phone").value((Object) null))
			.andExpect(jsonPath("$.email").value("created@example.com"))
			.andExpect(jsonPath("$.active").value(true))
			.andExpect(jsonPath("$.passwordHash").doesNotExist());

		User created = userMapper.selectOne(Wrappers.<User>lambdaQuery()
			.eq(User::getUsername, username));
		assertThat(passwordEncoder.matches("initial-test-password", created.getPasswordHash())).isTrue();
	}

	@Test
	void duplicateUsernameReturnsConflictWithoutCreatingAnotherUser() throws Exception {
		User admin = insertUser(true, SystemRole.ADMIN);
		User existing = insertUser(true);
		LoginSession adminSession = newSession();
		login(adminSession, admin.getUsername(), "test-password").andExpect(status().isOk());

		createUser(adminSession, new CreateUserBody(
			existing.getUsername(),
			"initial-test-password",
			"Duplicate User",
			SystemRole.USER,
			null,
			null))
			.andExpect(status().isConflict())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("USERNAME_ALREADY_EXISTS"))
			.andExpect(jsonPath("$.errors").isEmpty());

		assertThat(userMapper.selectCount(Wrappers.<User>lambdaQuery()
			.eq(User::getUsername, existing.getUsername()))).isOne();
	}

	@Test
	void nonAdminCannotCreateUser() throws Exception {
		User actor = insertUser(true);
		LoginSession actorSession = newSession();
		login(actorSession, actor.getUsername(), "test-password").andExpect(status().isOk());
		String username = "forbidden-" + UUID.randomUUID();

		createUser(actorSession, new CreateUserBody(
			username,
			"initial-test-password",
			"Forbidden User",
			SystemRole.USER,
			null,
			null))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));

		assertThat(userMapper.selectCount(Wrappers.<User>lambdaQuery()
			.eq(User::getUsername, username))).isZero();
	}

	@Test
	void createUserRequiresNullableFieldsAndValidatesValues() throws Exception {
		User admin = insertUser(true, SystemRole.ADMIN);
		LoginSession adminSession = newSession();
		login(adminSession, admin.getUsername(), "test-password").andExpect(status().isOk());

		mockMvc.perform(post("/api/users")
				.session(adminSession.session())
				.header(adminSession.headerName(), adminSession.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"username":"missing-phone","initialPassword":"initial-test-password",
					 "displayName":"Missing Phone","systemRole":"USER","email":null}
					"""))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
			.andExpect(jsonPath("$.errors[0].field").value("phone"));

		createUser(adminSession, new CreateUserBody(
			"invalid-email-" + UUID.randomUUID(),
			"initial-test-password",
			"Invalid Email",
			SystemRole.USER,
			null,
			"not-an-email"))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
			.andExpect(jsonPath("$.errors[0].field").value("email"));
	}

	@Test
	void adminReadsUserAndMissingUserReturnsNotFound() throws Exception {
		User admin = insertUser(true, SystemRole.ADMIN);
		User target = insertUser(true);
		LoginSession adminSession = newSession();
		login(adminSession, admin.getUsername(), "test-password").andExpect(status().isOk());

		mockMvc.perform(get("/api/users/{userId}", target.getId()).session(adminSession.session()))
			.andExpect(status().isOk())
			.andExpect(content().contentType(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.id").value(target.getId().toString()))
			.andExpect(jsonPath("$.username").value(target.getUsername()))
			.andExpect(jsonPath("$.passwordHash").doesNotExist());

		mockMvc.perform(get("/api/users/{userId}", Long.MAX_VALUE).session(adminSession.session()))
			.andExpect(status().isNotFound())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("NOT_FOUND"));
	}

	@Test
	void adminListsFilteredSortedUserPages() throws Exception {
		User admin = insertUser(true, SystemRole.ADMIN);
		String marker = "page-" + UUID.randomUUID();
		User first = insertUser(marker + "-a", marker + " First", true, SystemRole.USER);
		User second = insertUser(marker + "-b", marker + " Second", true, SystemRole.USER);
		insertUser(marker + "-inactive", marker + " Inactive", false, SystemRole.USER);
		insertUser(marker + "-admin", marker + " Admin", true, SystemRole.ADMIN);
		LoginSession adminSession = newSession();
		login(adminSession, admin.getUsername(), "test-password").andExpect(status().isOk());

		mockMvc.perform(get("/api/users")
				.session(adminSession.session())
				.param("q", marker)
				.param("systemRole", "USER")
				.param("active", "true")
				.param("page", "0")
				.param("size", "1")
				.param("sort", "username,desc"))
			.andExpect(status().isOk())
			.andExpect(content().contentType(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.items[0].id").value(second.getId().toString()))
			.andExpect(jsonPath("$.items[0].passwordHash").doesNotExist())
			.andExpect(jsonPath("$.page").value(0))
			.andExpect(jsonPath("$.size").value(1))
			.andExpect(jsonPath("$.totalElements").value(2))
			.andExpect(jsonPath("$.totalPages").value(2));

		mockMvc.perform(get("/api/users")
				.session(adminSession.session())
				.param("q", marker)
				.param("systemRole", "USER")
				.param("page", "1")
				.param("size", "1")
				.param("sort", "username,desc"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].id").value(first.getId().toString()));
	}

	@Test
	void nonAdminCannotReadManagedUsers() throws Exception {
		User actor = insertUser(true);
		User target = insertUser(true);
		LoginSession actorSession = newSession();
		login(actorSession, actor.getUsername(), "test-password").andExpect(status().isOk());

		mockMvc.perform(get("/api/users").session(actorSession.session()))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));
		mockMvc.perform(get("/api/users/{userId}", target.getId()).session(actorSession.session()))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));
	}

	@Test
	void userListRejectsInvalidQueryValues() throws Exception {
		User admin = insertUser(true, SystemRole.ADMIN);
		LoginSession adminSession = newSession();
		login(adminSession, admin.getUsername(), "test-password").andExpect(status().isOk());

		for (String[] invalid : new String[][] {
			{"active", "banana"},
			{"systemRole", "OWNER"},
			{"page", "-1"},
			{"size", "101"},
			{"sort", "username,sideways"}
		}) {
			mockMvc.perform(get("/api/users")
					.session(adminSession.session())
					.param(invalid[0], invalid[1]))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.errors[0].field").value(invalid[0]));
		}
	}

	private LoginSession newSession() throws Exception {
		MvcResult csrfResult = mockMvc.perform(get("/api/auth/csrf")).andReturn();
		JsonNode csrf = objectMapper.readTree(csrfResult.getResponse().getContentAsByteArray());
		return new LoginSession(
			(MockHttpSession) csrfResult.getRequest().getSession(false),
			csrf.get("headerName").asText(),
			csrf.get("token").asText());
	}

	private ResultActions login(LoginSession loginSession, String username, String password) throws Exception {
		return mockMvc.perform(post("/api/auth/login")
			.session(loginSession.session())
			.header(loginSession.headerName(), loginSession.token())
			.contentType(MediaType.APPLICATION_JSON)
			.content(objectMapper.writeValueAsBytes(new LoginBody(username, password))));
	}

	private ResultActions createUser(LoginSession loginSession, CreateUserBody body) throws Exception {
		return mockMvc.perform(post("/api/users")
			.session(loginSession.session())
			.header(loginSession.headerName(), loginSession.token())
			.contentType(MediaType.APPLICATION_JSON)
			.content(objectMapper.writeValueAsBytes(body)));
	}

	private ResultActions updateProfile(LoginSession loginSession, UpdateProfileBody body) throws Exception {
		return mockMvc.perform(put("/api/users/me")
			.session(loginSession.session())
			.header(loginSession.headerName(), loginSession.token())
			.contentType(MediaType.APPLICATION_JSON)
			.content(objectMapper.writeValueAsBytes(body)));
	}

	private User insertUser(boolean active) {
		return insertUser(active, SystemRole.USER);
	}

	private User insertUser(boolean active, SystemRole systemRole) {
		return insertUser(
			"session-" + UUID.randomUUID(),
			"Session Test",
			active,
			systemRole);
	}

	private User insertUser(String username, String displayName, boolean active, SystemRole systemRole) {
		User user = new User();
		user.setUsername(username);
		user.setPasswordHash(passwordEncoder.encode("test-password"));
		user.setDisplayName(displayName);
		user.setSystemRole(systemRole);
		user.setActive(active);
		userMapper.insert(user);
		return userMapper.selectById(user.getId());
	}

	record LoginBody(String username, String password) {
	}

	record ChangePasswordBody(String currentPassword, String newPassword) {
	}

	record ResetPasswordBody(String newPassword) {
	}

	record UpdateProfileBody(String displayName, String phone, String email) {
	}

	record CreateUserBody(
		String username,
		String initialPassword,
		String displayName,
		SystemRole systemRole,
		String phone,
		String email) {
	}

	record LoginSession(MockHttpSession session, String headerName, String token) {
	}
}
