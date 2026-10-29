package com.campus.events.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT utility: generates and validates JSON Web Tokens.
 * Uses HS256 (HMAC-SHA256) symmetric signing.
 * Secret is injected from application.properties (env variable backed).
 */
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expirationMs;

    private SecretKey getSigningKey() {
        byte[] keyBytes = secret.getBytes();
        // Ensure key is at least 256 bits for HS256
        if (keyBytes.length < 32) {
            throw new IllegalStateException("JWT secret must be at least 32 characters");
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Generate a JWT for a given user.
     * Uses HashMap (Java Collections) for claims.
     *
     * @param userId student/admin ID
     * @param role   STUDENT or ADMIN
     * @param name   display name
     * @return signed JWT string
     */
    public String generateToken(Long userId, String role, String name) {
        // Java Collections: HashMap to hold JWT claims
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", role);
        claims.put("name", name);
        claims.put("userId", userId);

        return Jwts.builder()
                .claims(claims)
                .subject(String.valueOf(userId))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Validate token and extract claims.
     * @return Claims object if valid, throws JwtException if invalid/expired
     */
    public Claims validateAndExtractClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long getUserId(String token) {
        return validateAndExtractClaims(token).get("userId", Long.class);
    }

    public String getRole(String token) {
        return (String) validateAndExtractClaims(token).get("role");
    }

    public boolean isTokenValid(String token) {
        try {
            validateAndExtractClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
