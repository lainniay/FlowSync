package hgc.flowsync.user;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public final class DatabaseUserDetailsService implements UserDetailsService {

	private final UserMapper userMapper;

	public DatabaseUserDetailsService(UserMapper userMapper) {
		this.userMapper = userMapper;
	}

	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		User user = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, username));
		if (user == null) {
			throw new UsernameNotFoundException("User not found");
		}
		return toUserDetails(user);
	}

	public static UserDetails toUserDetails(User user) {
		return org.springframework.security.core.userdetails.User.withUsername(user.getUsername())
			.password(user.getPasswordHash())
			.roles(user.getSystemRole().name())
			.disabled(!user.isActive())
			.build();
	}
}
