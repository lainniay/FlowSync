package hgc.flowsync.user;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import hgc.flowsync.common.error.BusinessException;
import hgc.flowsync.common.error.ErrorCode;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserWriteLockService {

	private final UserMapper userMapper;

	public UserWriteLockService(UserMapper userMapper) {
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

		Map<Long, User> users = lockUsersById(userIds);
		User lockedCurrentUser = users.get(currentUser.getId());
		if (lockedCurrentUser == null || !lockedCurrentUser.isActive()) {
			throw new BusinessException(ErrorCode.UNAUTHORIZED);
		}
		return new LockedUsers(lockedCurrentUser, Map.copyOf(users));
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public Map<Long, User> lockUsersById(Collection<Long> requestedUserIds) {
		Set<Long> userIds = new TreeSet<>(requestedUserIds);
		Map<Long, User> users = new HashMap<>();
		for (User user : userMapper.selectList(Wrappers.<User>lambdaQuery()
			.in(User::getId, userIds)
			.orderByAsc(User::getId)
			.last("FOR UPDATE"))) {
			users.put(user.getId(), user);
		}
		return Map.copyOf(users);
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void lockAdminRoleChanges() {
		// ponytail: users are not physically deleted, so the lowest ID is a stable lock row.
		userMapper.selectList(Wrappers.<User>lambdaQuery()
			.select(User::getId)
			.orderByAsc(User::getId)
			.last("LIMIT 1 FOR UPDATE"));
	}

	public record LockedUsers(User currentUser, Map<Long, User> users) {

		public User user(Long userId) {
			return users.get(userId);
		}
	}
}
