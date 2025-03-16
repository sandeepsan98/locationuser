package com.pro.location.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;

import java.util.Date;
import java.util.UUID;
import java.util.logging.Logger;

public class JwtUtil {
    private static final Logger LOGGER = Logger.getLogger(JwtUtil.class.getName());
    private static final String SECRET_KEY = "your-32-byte-long-secret-key-here!!"; // Must be 32+ bytes
    private static final long JWT_EXPIRY_MS = 15 * 60 * 1000; // 15 minutes
    private static final long REFRESH_EXPIRY_MS = 7 * 24 * 60 * 60 * 1000; // 7 days

    public static String generateJwt(int userId) {
        LOGGER.info("Generating JWT for user: " + userId);
        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .claim("role", "user")
                .setIssuer("simulator")
                .setAudience("api")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + JWT_EXPIRY_MS))
                .signWith(Keys.hmacShaKeyFor(SECRET_KEY.getBytes()), SignatureAlgorithm.HS256)
                .compact();
    }

    public static String generateRefreshToken() {
        String token = UUID.randomUUID().toString();
        LOGGER.info("Generated refresh token: " + token);
        return token;
    }

    public static Claims validateJwt(String token) throws JwtException {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(Keys.hmacShaKeyFor(SECRET_KEY.getBytes()))
                    .requireIssuer("simulator")
                    .requireAudience("api")
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            LOGGER.info("JWT validated for user: " + claims.getSubject());
            return claims;
        } catch (JwtException e) {
            LOGGER.warning("JWT validation failed: " + e.getMessage());
            throw e;
        }
    }

    public static long getRefreshExpiryMs() {
        return REFRESH_EXPIRY_MS;
    }
}