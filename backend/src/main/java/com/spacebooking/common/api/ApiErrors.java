package com.spacebooking.common.api;

import com.spacebooking.common.domain.ErrorCode;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Error codes an endpoint can answer with. Read only by the OpenAPI customizer, which turns them into ProblemDetail
 * responses (500 INTERNAL_ERROR is always added).
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiErrors {

    ErrorCode[] value();
}
