package com.chatops.global.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an endpoint for Redis-based rate limiting.
 * When {@code byUser} is false, rate limiting is applied per IP address (for unauthenticated endpoints).
 * When {@code byUser} is true, rate limiting is applied per authenticated user ID.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {
    /** Maximum number of requests allowed within the time window. */
    int maxRequests() default 60;

    /** Time window in seconds. */
    int windowSeconds() default 60;

    /** If true, rate limit by authenticated user ID; if false, by IP address. */
    boolean byUser() default false;
}
