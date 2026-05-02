package edu.ai.dyslexiaprisonbackend.util.validUtils.annotations;


import edu.ai.dyslexiaprisonbackend.util.validUtils.validators.PasswordValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = {PasswordValidator.class})
@Target(ElementType.FIELD)
public @interface Password {
    String message() default  "Invalid Credentials";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
