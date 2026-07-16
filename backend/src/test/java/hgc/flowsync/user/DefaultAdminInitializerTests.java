package hgc.flowsync.user;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest
class DefaultAdminInitializerTests {

	private final UserMapper userMapper;

	@Autowired
	DefaultAdminInitializerTests(UserMapper userMapper) {
		this.userMapper = userMapper;
	}

	@Test
	void startupLeavesAnActiveAdmin() {
		long activeAdmins = userMapper.selectCount(Wrappers.<User>lambdaQuery()
			.eq(User::getSystemRole, SystemRole.ADMIN)
			.eq(User::isActive, true));

		assertThat(activeAdmins).isPositive();
	}

	@Test
	void existingAdminDoesNotRequireBootstrapCredentials() {
		UserMapper mapper = mock(UserMapper.class);
		PasswordEncoder encoder = mock(PasswordEncoder.class);
		when(mapper.selectCount(any())).thenReturn(1L);

		new ApplicationContextRunner()
			.withPropertyValues("DEFAULT_ADMIN_USERNAME=", "DEFAULT_ADMIN_PASSWORD=")
			.withBean(UserMapper.class, () -> mapper)
			.withBean(PasswordEncoder.class, () -> encoder)
			.withUserConfiguration(DefaultAdminInitializer.class)
			.run(context -> {
				assertThat(context.getStartupFailure()).isNull();
				context.getBean(DefaultAdminInitializer.class).run(null);
				verifyNoInteractions(encoder);
			});
	}
}
