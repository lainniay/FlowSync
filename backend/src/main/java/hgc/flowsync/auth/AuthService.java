package hgc.flowsync.auth;

import hgc.flowsync.common.error.BusinessException;
import hgc.flowsync.common.error.ErrorCode;
import hgc.flowsync.user.CurrentUserService;
import hgc.flowsync.user.DatabaseUserDetailsService;
import hgc.flowsync.user.User;
import hgc.flowsync.user.UserMapper;
import hgc.flowsync.user.UserResponse;
import hgc.flowsync.user.UserService;
import hgc.flowsync.user.PasswordPolicy;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

	private final UserMapper userMapper;
	private final PasswordEncoder passwordEncoder;
	private final String dummyPasswordHash;
	private final SessionRegistry sessionRegistry;
	private final UserService userService;
	private final CurrentUserService currentUserService;
	private final SecurityContextHolderStrategy contextHolderStrategy =
		SecurityContextHolder.getContextHolderStrategy();
	private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();
	private final SessionAuthenticationStrategy sessionAuthenticationStrategy =
		new ChangeSessionIdAuthenticationStrategy();

	public AuthService(
		UserMapper userMapper,
		PasswordEncoder passwordEncoder,
		SessionRegistry sessionRegistry,
		UserService userService,
		CurrentUserService currentUserService) {
		this.userMapper = userMapper;
		this.passwordEncoder = passwordEncoder;
		this.dummyPasswordHash = passwordEncoder.encode("flowsync-dummy-password");
		this.sessionRegistry = sessionRegistry;
		this.userService = userService;
		this.currentUserService = currentUserService;
	}

	@Transactional
	public UserResponse login(
		String username,
		String password,
		HttpServletRequest request,
		HttpServletResponse response) {
		if (!PasswordPolicy.isBcryptCompatible(password)) {
			throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
		}
		User user = userMapper.selectOne(Wrappers.<User>lambdaQuery()
			.eq(User::getUsername, username)
			.last("FOR UPDATE"));
		boolean passwordMatches = passwordEncoder.matches(
			password,
			user == null ? dummyPasswordHash : user.getPasswordHash());
		if (user == null || !user.isActive() || !passwordMatches) {
			throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
		}
		var principal = DatabaseUserDetailsService.toUserDetails(user);
		Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
			principal, null, principal.getAuthorities());
		sessionAuthenticationStrategy.onAuthentication(authentication, request, response);
		SecurityContext context = contextHolderStrategy.createEmptyContext();
		context.setAuthentication(authentication);
		contextHolderStrategy.setContext(context);
		contextRepository.saveContext(context, request, response);
		sessionRegistry.registerNewSession(request.getSession().getId(), authentication.getName());
		return currentUser(authentication);
	}

	public UserResponse currentUser(Authentication authentication) {
		return UserResponse.from(currentUserService.require(authentication));
	}

	@Transactional
	public UserResponse updateProfile(
		Authentication authentication,
		String displayName,
		String phone,
		String email) {
		return userService.updateProfile(
			currentUserService.require(authentication),
			displayName,
			phone,
			email);
	}

	@Transactional
	public void changePassword(Authentication authentication, String currentPassword, String newPassword) {
		User user = currentUserService.requireForUpdate(authentication);
		if (!PasswordPolicy.isBcryptCompatible(currentPassword)
			|| !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
			throw new BusinessException(ErrorCode.CURRENT_PASSWORD_INCORRECT);
		}
		userService.updatePassword(user, newPassword);
	}
}
