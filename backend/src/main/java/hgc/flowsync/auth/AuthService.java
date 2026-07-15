package hgc.flowsync.auth;

import hgc.flowsync.common.error.BusinessException;
import hgc.flowsync.common.error.ErrorCode;
import hgc.flowsync.user.User;
import hgc.flowsync.user.UserMapper;
import hgc.flowsync.user.UserResponse;
import hgc.flowsync.user.UserService;
import hgc.flowsync.user.PasswordPolicy;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AccountStatusException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
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

	private final AuthenticationManager authenticationManager;
	private final UserMapper userMapper;
	private final PasswordEncoder passwordEncoder;
	private final SessionRegistry sessionRegistry;
	private final UserService userService;
	private final SecurityContextHolderStrategy contextHolderStrategy =
		SecurityContextHolder.getContextHolderStrategy();
	private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();
	private final SessionAuthenticationStrategy sessionAuthenticationStrategy =
		new ChangeSessionIdAuthenticationStrategy();

	public AuthService(
		AuthenticationManager authenticationManager,
		UserMapper userMapper,
		PasswordEncoder passwordEncoder,
		SessionRegistry sessionRegistry,
		UserService userService) {
		this.authenticationManager = authenticationManager;
		this.userMapper = userMapper;
		this.passwordEncoder = passwordEncoder;
		this.sessionRegistry = sessionRegistry;
		this.userService = userService;
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
		userMapper.selectOne(Wrappers.<User>lambdaQuery()
			.eq(User::getUsername, username)
			.last("FOR UPDATE"));
		Authentication authentication;
		try {
			authentication = authenticationManager.authenticate(
				UsernamePasswordAuthenticationToken.unauthenticated(username, password));
		} catch (BadCredentialsException | AccountStatusException exception) {
			throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
		}
		sessionAuthenticationStrategy.onAuthentication(authentication, request, response);
		SecurityContext context = contextHolderStrategy.createEmptyContext();
		context.setAuthentication(authentication);
		contextHolderStrategy.setContext(context);
		contextRepository.saveContext(context, request, response);
		sessionRegistry.registerNewSession(request.getSession().getId(), authentication.getName());
		return currentUser(authentication);
	}

	public UserResponse currentUser(Authentication authentication) {
		return UserResponse.from(currentUserEntity(authentication));
	}

	@Transactional
	public UserResponse updateProfile(
		Authentication authentication,
		String displayName,
		String phone,
		String email) {
		return userService.updateProfile(
			currentUserEntity(authentication),
			displayName,
			phone,
			email);
	}

	@Transactional
	public void changePassword(Authentication authentication, String currentPassword, String newPassword) {
		User user = currentUserEntity(authentication, true);
		if (!PasswordPolicy.isBcryptCompatible(currentPassword)
			|| !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
			throw new BusinessException(ErrorCode.CURRENT_PASSWORD_INCORRECT);
		}
		userService.updatePassword(user, newPassword);
	}

	private User currentUserEntity(Authentication authentication) {
		return currentUserEntity(authentication, false);
	}

	private User currentUserEntity(Authentication authentication, boolean forUpdate) {
		var query = Wrappers.<User>lambdaQuery().eq(User::getUsername, authentication.getName());
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
