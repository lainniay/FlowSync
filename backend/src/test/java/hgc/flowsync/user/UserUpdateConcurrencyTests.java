package hgc.flowsync.user;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import hgc.flowsync.common.error.BusinessException;
import hgc.flowsync.common.error.ErrorCode;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class UserUpdateConcurrencyTests {

	@Autowired
	private UserService userService;
	@Autowired
	private UserMapper userMapper;
	@Autowired
	private PasswordEncoder passwordEncoder;

	@Test
	void concurrentUpdatesCannotRemoveEveryActiveAdmin() throws Exception {
		List<Long> originalAdminIds = userMapper.selectList(Wrappers.<User>lambdaQuery()
			.eq(User::getSystemRole, SystemRole.ADMIN)
			.eq(User::isActive, true)).stream().map(User::getId).toList();
		assertThat(originalAdminIds).isNotEmpty();
		String firstUsername = "cu-" + UUID.randomUUID();
		String secondUsername = "cu-" + UUID.randomUUID();

		try {
			userMapper.update(null, Wrappers.<User>lambdaUpdate()
				.in(User::getId, originalAdminIds)
				.set(User::isActive, false));
			User first = insertAdmin(firstUsername);
			User second = insertAdmin(secondUsername);
			CountDownLatch start = new CountDownLatch(1);
			try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
				Future<ErrorCode> firstResult = executor.submit(() -> deactivate(first, start));
				Future<ErrorCode> secondResult = executor.submit(() -> deactivate(second, start));
				start.countDown();
				assertThat(Arrays.asList(
					firstResult.get(5, TimeUnit.SECONDS),
					secondResult.get(5, TimeUnit.SECONDS)))
					.containsExactlyInAnyOrder(null, ErrorCode.LAST_ADMIN_REQUIRED);
			}

			assertThat(userMapper.selectCount(Wrappers.<User>lambdaQuery()
				.eq(User::getSystemRole, SystemRole.ADMIN)
				.eq(User::isActive, true))).isOne();
		} finally {
			userMapper.update(null, Wrappers.<User>lambdaUpdate()
				.in(User::getId, originalAdminIds)
				.set(User::isActive, true));
			userMapper.delete(Wrappers.<User>lambdaQuery()
				.in(User::getUsername, firstUsername, secondUsername));
		}
	}

	private ErrorCode deactivate(User user, CountDownLatch start) throws InterruptedException {
		start.await();
		try {
			userService.update(
				user.getId(),
				user.getDisplayName(),
				null,
				null,
				SystemRole.ADMIN,
				false);
			return null;
		} catch (BusinessException exception) {
			return exception.code();
		}
	}

	private User insertAdmin(String username) {
		User user = new User();
		user.setUsername(username);
		user.setPasswordHash(passwordEncoder.encode("test-password"));
		user.setDisplayName("Concurrent Admin");
		user.setSystemRole(SystemRole.ADMIN);
		userMapper.insert(user);
		return userMapper.selectById(user.getId());
	}
}
