package hgc.flowsync.user;

import hgc.flowsync.common.error.BusinessException;
import hgc.flowsync.common.error.ErrorCode;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {

	private final UserMapper userMapper;

	public CurrentUserService(UserMapper userMapper) {
		this.userMapper = userMapper;
	}

	public User require(Authentication authentication) {
		return require(authentication, false);
	}

	public User requireForUpdate(Authentication authentication) {
		return require(authentication, true);
	}

	private User require(Authentication authentication, boolean forUpdate) {
		if (authentication == null) {
			throw new BusinessException(ErrorCode.UNAUTHORIZED);
		}
		var query = Wrappers.<User>lambdaQuery()
			.eq(User::getUsername, authentication.getName());
		if (forUpdate) {
			query.last("FOR UPDATE");
		}
		User user = userMapper.selectOne(query);
		if (user == null || !user.isActive()) {
			throw new BusinessException(ErrorCode.UNAUTHORIZED);
		}
		return user;
	}
}
