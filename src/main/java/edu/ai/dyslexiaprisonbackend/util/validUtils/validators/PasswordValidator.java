package edu.ai.dyslexiaprisonbackend.util.validUtils.validators;


import edu.ai.dyslexiaprisonbackend.util.validUtils.annotations.Password;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PasswordValidator implements ConstraintValidator<Password,String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value.matches("^(?=.*[A-Za-z])(?=.*\\d).{8,}$");
    }
}
