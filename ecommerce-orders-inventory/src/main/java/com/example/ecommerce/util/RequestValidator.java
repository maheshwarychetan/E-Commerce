package com.example.ecommerce.util;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Thin wrapper around Jakarta Bean Validation, used at the service-layer
 * boundary before any DTO's fields are read. This is where "Implement
 * proper validation at service/input boundaries" (annotated @NotBlank,
 * @DecimalMin, @Min, @NotEmpty, etc. on the dto classes) is actually
 * enforced, since this console application has no Spring MVC pipeline to
 * trigger @Valid automatically.
 */
@Component
public class RequestValidator {

    private final Validator validator;

    public RequestValidator(Validator validator) {
        this.validator = validator;
    }

    public <T> void validate(T request) {
        Set<ConstraintViolation<T>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            String message = violations.stream()
                    .map(ConstraintViolation::getMessage)
                    .collect(Collectors.joining("; "));
            throw new IllegalArgumentException(message);
        }
    }
}
