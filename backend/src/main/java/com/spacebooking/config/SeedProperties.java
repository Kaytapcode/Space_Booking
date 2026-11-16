package com.spacebooking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.seed")
public record SeedProperties(boolean enabled, String adminPassword, String managerPassword, String employeePassword) {
}
