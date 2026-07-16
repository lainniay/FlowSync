package hgc.flowsync.user;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

@Component
public class DefaultAdminInitializer implements ApplicationRunner {

	private final UserMapper userMapper;
	private final PasswordEncoder passwordEncoder;
	private final String username;
	private final String password;

	public DefaultAdminInitializer(
		UserMapper userMapper,
		PasswordEncoder passwordEncoder,
		@Value("${DEFAULT_ADMIN_USERNAME:}") String username,
		@Value("${DEFAULT_ADMIN_PASSWORD:}") String password) {
		this.userMapper = userMapper;
		this.passwordEncoder = passwordEncoder;
		this.username = username;
		this.password = password;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments arguments) {
		long activeAdmins = userMapper.selectCount(Wrappers.<User>lambdaQuery()
			.eq(User::getSystemRole, SystemRole.ADMIN)
			.eq(User::isActive, true));
		if (activeAdmins > 0) {
			return;
		}
		Assert.hasText(username, "DEFAULT_ADMIN_USERNAME must not be blank");
		Assert.isTrue(username.length() <= 50, "DEFAULT_ADMIN_USERNAME must not exceed 50 characters");
		Assert.hasText(password, "DEFAULT_ADMIN_PASSWORD must not be blank");
		Assert.isTrue(PasswordPolicy.isValid(password),
			"DEFAULT_ADMIN_PASSWORD must contain 12 to 64 characters and at most 72 UTF-8 bytes");
		User admin = new User();
		admin.setUsername(username);
		admin.setPasswordHash(passwordEncoder.encode(password));
		admin.setDisplayName(username);
		admin.setSystemRole(SystemRole.ADMIN);
		userMapper.insert(admin);
	}
}
