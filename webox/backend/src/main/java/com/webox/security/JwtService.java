package com.webox.security;

import com.webox.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Issues and verifies HS256 JSON Web Tokens.
 *
 * <p>No secret is committed to the repository. If {@code WEBOX_JWT_SECRET} is absent an
 * ephemeral random key is generated so a reviewer can start the app with zero setup; the
 * trade-off (all tokens become invalid on restart) is logged loudly.
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_EMAIL = "email";
    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;
    private final Duration ttl;

    public JwtService(com.webox.config.WeBoxProperties properties) {
        String configured = properties.jwt().secret();
        if (configured == null || configured.isBlank()) {
            byte[] random = new byte[MIN_SECRET_BYTES];
            new SecureRandom().nextBytes(random);
            this.key = Keys.hmacShaKeyFor(random);
            log.warn("WEBOX_JWT_SECRET is not set; generated an ephemeral signing key. "
                    + "Every token becomes invalid when the application restarts. "
                    + "Set WEBOX_JWT_SECRET for any real deployment.");
        } else {
            byte[] bytes = configured.getBytes(StandardCharsets.UTF_8);
            if (bytes.length < MIN_SECRET_BYTES) {
                throw new IllegalStateException(
                        "WEBOX_JWT_SECRET must be at least " + MIN_SECRET_BYTES + " bytes for HS256.");
            }
            this.key = Keys.hmacShaKeyFor(bytes);
        }
        this.ttl = Duration.ofHours(properties.jwt().ttlHours());
    }

    public String issue(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim(CLAIM_ROLE, user.getRole().name())
                .claim(CLAIM_EMAIL, user.getEmail())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    public long ttlSeconds() {
        return ttl.toSeconds();
    }

    /** @return the authenticated principal, or empty when the token is absent/invalid/expired. */
    public Optional<AuthPrincipal> parse(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Long userId = Long.valueOf(claims.getSubject());
            com.webox.user.UserRole role =
                    com.webox.user.UserRole.valueOf(claims.get(CLAIM_ROLE, String.class));
            return Optional.of(new AuthPrincipal(userId, role));
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Rejected JWT: {}", ex.getMessage());
            return Optional.empty();
        }
    }
}
