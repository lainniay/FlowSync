package hgc.flowsync.user;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

public final class PasswordPolicy {

	private PasswordPolicy() {
	}

	public static boolean isValid(String password) {
		if (password == null) {
			return false;
		}
		int characters = password.codePointCount(0, password.length());
		return characters >= 12
			&& characters <= 64
			&& password.getBytes(StandardCharsets.UTF_8).length <= 72;
	}

	public static boolean isBcryptCompatible(String password) {
		return password != null && password.getBytes(StandardCharsets.UTF_8).length <= 72;
	}

	@Documented
	@Constraint(validatedBy = Validator.class)
	@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
	@Retention(RetentionPolicy.RUNTIME)
	public @interface Valid {

		String message() default "must contain 12 to 64 characters and at most 72 UTF-8 bytes";

		Class<?>[] groups() default {};

		Class<? extends Payload>[] payload() default {};
	}

	public static final class Validator implements ConstraintValidator<Valid, String> {

		@Override
		public boolean isValid(String value, ConstraintValidatorContext context) {
			return value == null || PasswordPolicy.isValid(value);
		}
	}
}
