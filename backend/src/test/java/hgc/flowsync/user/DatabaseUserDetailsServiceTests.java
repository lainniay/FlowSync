package hgc.flowsync.user;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class DatabaseUserDetailsServiceTests {

	private final UserMapper userMapper;
	private final DatabaseUserDetailsService userDetailsService;
	private final PasswordEncoder passwordEncoder;

	@Autowired
	DatabaseUserDetailsServiceTests(
		UserMapper userMapper,
		DatabaseUserDetailsService userDetailsService,
		PasswordEncoder passwordEncoder) {
		this.userMapper = userMapper;
		this.userDetailsService = userDetailsService;
		this.passwordEncoder = passwordEncoder;
	}

	@Test
	void loadUserMapsCredentialsRoleAndActiveState() {
		User user = user("auth-" + UUID.randomUUID(), true);
		userMapper.insert(user);

		UserDetails details = userDetailsService.loadUserByUsername(user.getUsername());

		assertThat(details.getUsername()).isEqualTo(user.getUsername());
		assertThat(passwordEncoder.matches("test-password", details.getPassword())).isTrue();
		assertThat(details.getAuthorities()).extracting("authority").containsExactly("ROLE_USER");
		assertThat(details.isEnabled()).isTrue();

		User inactive = user("inactive-" + UUID.randomUUID(), false);
		userMapper.insert(inactive);
		assertThat(userDetailsService.loadUserByUsername(inactive.getUsername()).isEnabled()).isFalse();
	}

	@Test
	void missingUsernameIsNotAuthenticated() {
		assertThatThrownBy(() -> userDetailsService.loadUserByUsername("missing-" + UUID.randomUUID()))
			.isInstanceOf(UsernameNotFoundException.class);
	}

	private User user(String username, boolean active) {
		User user = new User();
		user.setUsername(username);
		user.setPasswordHash(passwordEncoder.encode("test-password"));
		user.setDisplayName("Auth Test");
		user.setSystemRole(SystemRole.USER);
		user.setActive(active);
		return user;
	}
}
