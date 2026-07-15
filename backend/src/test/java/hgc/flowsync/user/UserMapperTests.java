package hgc.flowsync.user;

import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class UserMapperTests {

	private final UserMapper userMapper;
	private final ObjectMapper objectMapper;

	@Autowired
	UserMapperTests(UserMapper userMapper, ObjectMapper objectMapper) {
		this.userMapper = userMapper;
		this.objectMapper = objectMapper;
	}

	@Test
	void insertAndSelectMapUserFields() throws Exception {
		User user = new User();
		user.setUsername("mapper-" + UUID.randomUUID());
		user.setPasswordHash("test-password-hash");
		user.setDisplayName("Mapper Test");
		user.setPhone("13800138000");
		user.setEmail("mapper@example.com");
		user.setSystemRole(SystemRole.USER);

		assertThat(userMapper.insert(user)).isOne();
		assertThat(user.getId()).isNotNull();

		User saved = userMapper.selectById(user.getId());
		assertThat(saved.getUsername()).isEqualTo(user.getUsername());
		assertThat(saved.getPasswordHash()).isEqualTo("test-password-hash");
		assertThat(saved.getDisplayName()).isEqualTo("Mapper Test");
		assertThat(saved.getPhone()).isEqualTo("13800138000");
		assertThat(saved.getEmail()).isEqualTo("mapper@example.com");
		assertThat(saved.getSystemRole()).isEqualTo(SystemRole.USER);
		assertThat(saved.isActive()).isTrue();
		assertThat(saved.getCreatedAt()).isNotNull();
		assertThat(saved.getUpdatedAt()).isNotNull();
		assertThat(objectMapper.writeValueAsString(saved)).doesNotContain("passwordHash", "test-password-hash");
	}
}
