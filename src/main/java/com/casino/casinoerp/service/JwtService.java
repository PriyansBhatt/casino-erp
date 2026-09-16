package com.casino.casinoerp.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Date;
import java.util.HexFormat;
import java.util.Locale;

@Service
public class JwtService {
    private final SecretKey signingKey;
    private static final long EXPIRATION_TIME = 1000 * 60 * 60 * 8;
    public JwtService(@Value("${casino.security.jwt.secret:}") String secret) {
        byte[] bytes=secret.getBytes(StandardCharsets.UTF_8);
        String lower=secret.toLowerCase(Locale.ROOT);
        if(secret.isBlank() || bytes.length<64 || secret.chars().distinct().count()<16
                || lower.contains("placeholder") || lower.contains("changeme") || lower.contains("change-me")
                || lower.contains("development") || lower.contains("test-only") || lower.contains("example")
                || fingerprint(bytes).equals("da7541b075f763e058f84d8f57f53ba84544db143258bc1e312ab8d0af4c2a0b"))
            throw new IllegalStateException("JWT_SIGNING_SECRET must be an externally supplied strong random secret of at least 64 UTF-8 bytes.");
        signingKey=Keys.hmacShaKeyFor(bytes);
    }
    private static String fingerprint(byte[] value) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));}
        catch(java.security.NoSuchAlgorithmException ex){throw new IllegalStateException("SHA-256 unavailable");}
    }
    public String generateToken(String username,String role) {
        return Jwts.builder().subject(username).claim("role",role).issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis()+EXPIRATION_TIME)).signWith(signingKey,Jwts.SIG.HS512).compact();
    }
    public String extractUsername(String token) {return Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload().getSubject();}
    public String extractRole(String token) {return Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload().get("role",String.class);}
}
