package com.example.capstone.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Token creation and validation. The "legacy" tokens below are fixed strings built independently of the library
 * (HS256, the shape the 0.11.x library issued), so these tests also prove that tokens already held by logged-in users
 * stay valid after the JWT library is upgraded.
 */
class JwtUtilTest {

    private static final String SECRET = "and0LXVuaXQtdGVzdC1zZWNyZXQta2V5LTAxMjM0NTY3ODlhYmNkZWYtMDEyMzQ1Njc4OQ==";
    private static final String LEGACY_ACCESS = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJsZWdhY3lAZXhhbXBsZS5jb20iLCJ0eXBlIjoiYWNjZXNzIiwiaWF0IjoxNzkwMDAwMDAwLCJleHAiOjQxMDI0NDQ4MDB9.kVpIGd1LuDXHyfUyFeyddXWiuDSq5chPqIWPC5ILUJI";
    private static final String LEGACY_REFRESH = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJsZWdhY3lAZXhhbXBsZS5jb20iLCJ0eXBlIjoicmVmcmVzaCIsImlhdCI6MTc5MDAwMDAwMCwiZXhwIjo0MTAyNDQ0ODAwfQ.GDag7gVl5DeygTmW3gFaLKhWg3t3eGalvMVDPzSsJPk";
    private static final String LEGACY_EXPIRED = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJsZWdhY3lAZXhhbXBsZS5jb20iLCJ0eXBlIjoiYWNjZXNzIiwiaWF0IjoxNzkwMDAwMDAwLCJleHAiOjE3MDAwMDAwMDB9.LRJiDHCQucXge1AmX3FB1tgCRb6uZ5GB4ihIOtTPSrE";

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", SECRET);
        ReflectionTestUtils.setField(jwtUtil, "expirationMs", 3_600_000L);
        ReflectionTestUtils.setField(jwtUtil, "refreshExpirationMs", 86_400_000L);
    }

    @Test
    void accessToken_roundTripsAndExpiresInTheFuture() {
        String token = jwtUtil.generateAccessToken("user@example.com");

        assertTrue(jwtUtil.validateToken(token));
        assertEquals("user@example.com", jwtUtil.extractEmail(token));
        Date exp = jwtUtil.extractExpiration(token);
        long millisLeft = exp.getTime() - System.currentTimeMillis();
        assertTrue(millisLeft > 3_500_000L && millisLeft <= 3_600_000L, "expires in about an hour, was " + millisLeft);
    }

    @Test
    void refreshToken_livesLongerAndCarriesItsType() {
        String access = jwtUtil.generateAccessToken("user@example.com");
        String refresh = jwtUtil.generateRefreshToken("user@example.com");

        assertTrue(jwtUtil.extractExpiration(refresh).after(jwtUtil.extractExpiration(access)));
        assertTrue(typeClaim(refresh).equals("refresh") && typeClaim(access).equals("access"));
    }

    @Test
    void tokensAreHs256() {
        String header = new String(Base64.getUrlDecoder().decode(jwtUtil.generateAccessToken("a@b.c").split("\\.")[0]));

        assertTrue(header.contains("\"alg\":\"HS256\""), header);
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = jwtUtil.generateAccessToken("user@example.com");
        String[] parts = token.split("\\.");
        String forgedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"admin@example.com\",\"type\":\"access\",\"exp\":4102444800}".getBytes());

        assertFalse(jwtUtil.validateToken(parts[0] + "." + forgedPayload + "." + parts[2]));
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        JwtUtil other = new JwtUtil();
        ReflectionTestUtils.setField(other, "secret", Base64.getEncoder().encodeToString("another-secret-another-secret-another-secret-12".getBytes()));
        ReflectionTestUtils.setField(other, "expirationMs", 3_600_000L);
        ReflectionTestUtils.setField(other, "refreshExpirationMs", 3_600_000L);

        assertFalse(jwtUtil.validateToken(other.generateAccessToken("user@example.com")));
    }

    @Test
    void expiredTokenIsRejected() {
        ReflectionTestUtils.setField(jwtUtil, "expirationMs", -1000L);

        assertFalse(jwtUtil.validateToken(jwtUtil.generateAccessToken("user@example.com")));
    }

    @Test
    void garbageIsRejectedWithoutThrowing() {
        assertFalse(jwtUtil.validateToken("not-a-jwt"));
        assertFalse(jwtUtil.validateToken(""));
    }

    @Test
    void unsignedTokenIsRejected() {
        String header = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"none\"}".getBytes());
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"sub\":\"admin@example.com\",\"exp\":4102444800}".getBytes());

        assertFalse(jwtUtil.validateToken(header + "." + payload + "."));
    }

    @Test
    void legacyTokensKeepWorking() {
        assertTrue(jwtUtil.validateToken(LEGACY_ACCESS));
        assertEquals("legacy@example.com", jwtUtil.extractEmail(LEGACY_ACCESS));
        assertEquals("access", typeClaim(LEGACY_ACCESS));
        assertEquals("refresh", typeClaim(LEGACY_REFRESH));
        assertEquals(4102444800L, jwtUtil.extractExpiration(LEGACY_ACCESS).getTime() / 1000);
    }

    @Test
    void legacyExpiredTokenStaysExpired() {
        assertFalse(jwtUtil.validateToken(LEGACY_EXPIRED));
        assertThrows(RuntimeException.class, () -> jwtUtil.extractEmail(LEGACY_EXPIRED));
    }

    /** Reads the type claim from the payload without going through the code under test. */
    private String typeClaim(String token) {
        String payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]));
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"type\":\"(\\w+)\"").matcher(payload);
        return m.find() ? m.group(1) : null;
    }
}
