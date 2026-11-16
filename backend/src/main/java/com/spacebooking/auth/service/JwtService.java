package com.spacebooking.auth.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spacebooking.user.domain.Role;
import com.spacebooking.user.domain.User;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

/** HS256 JWT on the JDK's HMAC; the project stack has no JWT library. */
@Service
public class JwtService {

    private static final String HMAC = "HmacSHA256";
    private static final int MIN_SECRET_BYTES = 32;
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String HEADER = ENCODER.encodeToString(
            "{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));

    private final SecretKeySpec key;
    private final JwtProperties props;
    private final Clock clock;

    public JwtService(JwtProperties props, Clock clock) {
        byte[] secret = props.secret() == null ? new byte[0] : props.secret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "app.jwt.secret (JWT_SECRET) must be set and at least " + MIN_SECRET_BYTES + " bytes long");
        }
        this.key = new SecretKeySpec(secret, HMAC);
        this.props = props;
        this.clock = clock;
    }

    public String issue(User user) {
        Instant now = clock.instant();
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", user.getId().toString());
        claims.put("email", user.getEmail());
        claims.put("role", user.getRole().name());
        claims.put("iat", now.getEpochSecond());
        claims.put("exp", now.plus(props.ttl()).getEpochSecond());
        try {
            String signingInput = HEADER + "." + ENCODER.encodeToString(JSON.writeValueAsBytes(claims));
            return signingInput + "." + ENCODER.encodeToString(sign(signingInput));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot issue token", e);
        }
    }

    public Optional<JwtClaims> verify(String token) {
        try {
            return Optional.of(parse(token));
        } catch (Exception e) {
            // Any malformed, tampered or expired token is simply unauthenticated.
            return Optional.empty();
        }
    }

    private JwtClaims parse(String token) throws GeneralSecurityException, IOException {
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3) {
            throw new IllegalArgumentException("not a compact JWS");
        }
        JsonNode header = JSON.readTree(DECODER.decode(parts[0]));
        if (!"HS256".equals(header.path("alg").asText())) {
            throw new IllegalArgumentException("unsupported alg");
        }
        byte[] expected = sign(parts[0] + "." + parts[1]);
        if (!MessageDigest.isEqual(expected, DECODER.decode(parts[2]))) {
            throw new IllegalArgumentException("bad signature");
        }
        JsonNode payload = JSON.readTree(DECODER.decode(parts[1]));
        if (!payload.path("exp").isIntegralNumber()) {
            throw new IllegalArgumentException("missing exp");
        }
        Instant exp = Instant.ofEpochSecond(payload.path("exp").asLong());
        if (!clock.instant().isBefore(exp)) {
            throw new IllegalArgumentException("expired");
        }
        return new JwtClaims(
                UUID.fromString(payload.path("sub").asText()),
                payload.path("email").asText(),
                Role.valueOf(payload.path("role").asText()),
                Instant.ofEpochSecond(payload.path("iat").asLong()),
                exp);
    }

    private byte[] sign(String signingInput) throws GeneralSecurityException {
        Mac mac = Mac.getInstance(HMAC);
        mac.init(key);
        return mac.doFinal(signingInput.getBytes(StandardCharsets.US_ASCII));
    }
}
