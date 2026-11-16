package com.spacebooking.common.api;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Collection;
import java.util.HashSet;

/** OpenAPI `uniqueItems: true`. Null is valid; pair with `@NotNull` when required. */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = UniqueItems.Validator.class)
public @interface UniqueItems {

    String message() default "must contain unique items";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<UniqueItems, Collection<?>> {

        @Override
        public boolean isValid(Collection<?> items, ConstraintValidatorContext context) {
            return items == null || new HashSet<>(items).size() == items.size();
        }
    }
}
