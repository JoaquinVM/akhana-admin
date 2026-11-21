package com.akhana.akhana_admin.dto.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.nio.charset.StandardCharsets;

public class MaxUtf8BytesValidator implements ConstraintValidator<MaxUtf8Bytes, String> {

    private int max;

    @Override
    public void initialize(MaxUtf8Bytes annotation) {
        max = annotation.value();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // Null is for @NotBlank, and more characters than bytes allowed is for @Size
        return value == null || value.length() > max || value.getBytes(StandardCharsets.UTF_8).length <= max;
    }
}
