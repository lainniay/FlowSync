package hgc.flowsync.common.security;

import hgc.flowsync.common.error.ErrorCode;
import hgc.flowsync.common.error.ProblemDetailResponseWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.session.HttpSessionEventPublisher;

@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class SecurityConfig {

	@Bean
	@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
	SecurityFilterChain securityFilterChain(
		HttpSecurity http,
		ProblemDetailResponseWriter problemWriter,
		SessionRegistry sessionRegistry) throws Exception {
		return http
			.authorizeHttpRequests(requests -> requests
				.requestMatchers(
					"/api/auth/csrf",
					"/api/auth/login",
					"/actuator/health",
					"/actuator/info",
					"/v3/api-docs/**",
					"/swagger-ui/**")
				.permitAll()
				.anyRequest().authenticated())
			.exceptionHandling(exceptions -> exceptions
				.authenticationEntryPoint((request, response, exception) -> problemWriter.write(
					request,
					response,
					ErrorCode.UNAUTHORIZED,
					"Unauthorized",
					"Authentication is required."))
				.accessDeniedHandler((request, response, exception) -> {
					boolean csrfFailure = exception instanceof CsrfException;
					problemWriter.write(
						request,
						response,
						csrfFailure ? ErrorCode.CSRF_INVALID : ErrorCode.FORBIDDEN,
						csrfFailure ? "Invalid CSRF token" : "Forbidden",
						csrfFailure
							? "The CSRF token is missing or invalid."
							: "You do not have permission to perform this action.");
				}))
			.sessionManagement(session -> session
				.maximumSessions(-1)
				.sessionRegistry(sessionRegistry)
				.expiredSessionStrategy(event -> problemWriter.write(
					event.getRequest(),
					event.getResponse(),
					ErrorCode.UNAUTHORIZED,
					"Unauthorized",
					"Authentication is required.")))
			.formLogin(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.build();
	}

	@Bean
	@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
	AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
		return configuration.getAuthenticationManager();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	SessionRegistry sessionRegistry() {
		return new SessionRegistryImpl();
	}

	@Bean
	@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
	HttpSessionEventPublisher httpSessionEventPublisher() {
		return new HttpSessionEventPublisher();
	}
}
