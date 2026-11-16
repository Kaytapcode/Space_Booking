package com.spacebooking.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/** Caffeine settings live in `spring.cache.*` (application.yml). */
@Configuration
@EnableCaching
public class CacheConfig {
}
