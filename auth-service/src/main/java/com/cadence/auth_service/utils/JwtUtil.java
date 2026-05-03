package com.cadence.auth_service.utils;

import com.cadence.auth_service.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

@Component
public class JwtUtil {
    private final Key key;

    public JwtUtil(@Value("${jwt.secret-key}") String secretKey) {
        this.key = Keys.hmacShaKeyFor(
                secretKey.getBytes(StandardCharsets.UTF_8)
        );
    }

    public String generateToken(String username, long expiryMinutes) {
        return generateToken(username, null, null, expiryMinutes);
    }

    public String generateToken(User user, long expiryMinutes) {
        return generateToken(user.getEmail(), user.getId(), user.getRole().name(), expiryMinutes);
    }

    public String generateToken(String username, String userId, String role, long expiryMinutes) {
        return Jwts.builder()
                .setSubject(username)
                .claim("userId", userId)
                .claim("role", role)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expiryMinutes * 60 * 1000))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public String validateAndExtractUsername(String token) {
        try {
            return Jwts.parser()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody()
                    .getSubject();
        } catch (JwtException e) {
            return null;
        }
    }

    public JwtIdentity validateAndExtractIdentity(String token) {
        try {
            Claims claims = Jwts.parser()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            return new JwtIdentity(
                    claims.getSubject(),
                    claims.get("userId", String.class),
                    claims.get("role", String.class)
            );
        } catch (JwtException e) {
            return null;
        }
    }

    public record JwtIdentity(
            String email,
            String userId,
            String role
    ) {
    }
}
