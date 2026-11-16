package com.spacebooking.auth.service;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app.jwt")
public record JwtProperties(String secret, @DefaultValue("8h") Duration ttl) {
}
