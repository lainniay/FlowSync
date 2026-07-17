package hgc.flowsync.task;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import hgc.flowsync.common.error.BusinessException;
import hgc.flowsync.common.error.ErrorCode;
import hgc.flowsync.user.User;
import hgc.flowsync.user.UserMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskWriteLockService {

	private final UserMapper userMapper;

	public TaskWriteLockService(UserMapper userMapper) {
		this.userMapper = userMapper;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public LockedUsers lockUsers(User currentUser, Long... additionalUserIds) {
		Set<Long> userIds = new TreeSet<>();
		userIds.add(currentUser.getId());
		for (Long userId : additionalUserIds) {
			if (userId != null) {
				userIds.add(userId);
			}
		}

		Map<Long, User> users = new HashMap<>();
		userMapper.selectList(Wrappers.<User>lambdaQuery()
			.in(User::getId, userIds)
			.orderByAsc(User::getId)
			.last("FOR UPDATE"))
			.forEach(user -> users.put(user.getId(), user));
		User lockedCurrentUser = users.get(currentUser.getId());
		if (lockedCurrentUser == null || !lockedCurrentUser.isActive()) {
			throw new BusinessException(ErrorCode.UNAUTHORIZED);
		}
		return new LockedUsers(Map.copyOf(users));
	}

	public record LockedUsers(Map<Long, User> users) {

		public User user(Long userId) {
			return users.get(userId);
		}
	}
}
