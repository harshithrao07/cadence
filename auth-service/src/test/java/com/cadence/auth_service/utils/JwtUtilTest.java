package com.cadence.auth_service.utils;

import com.cadence.auth_service.model.Role;
import com.cadence.auth_service.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {

    // 64-char hex → 64 bytes, satisfies HS256 minimum
    private static final String SECRET = "a5dfe365679bf8351c0d8d9bea6989b4001d6b05f809a4e72368037953f047b7";

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(SECRET);
    }

    private User user(String id, String email, Role role) {
        return User.builder().id(id).name("Test").email(email).role(role).build();
    }

    // ── generateToken(User) ───────────────────────────────────────────────────

    @Test
    void generateToken_withUser_embedsEmailUserIdAndRole() {
        User u = user("user-1", "test@example.com", Role.USER);

        String token = jwtUtil.generateToken(u, 15);

        JwtUtil.JwtIdentity identity = jwtUtil.validateAndExtractIdentity(token);
        assertThat(identity).isNotNull();
        assertThat(identity.email()).isEqualTo("test@example.com");
        assertThat(identity.userId()).isEqualTo("user-1");
        assertThat(identity.role()).isEqualTo("USER");
    }

    @Test
    void generateToken_withAdminUser_embedsAdminRole() {
        User u = user("admin-1", "admin@example.com", Role.ADMIN);

        String token = jwtUtil.generateToken(u, 15);

        JwtUtil.JwtIdentity identity = jwtUtil.validateAndExtractIdentity(token);
        assertThat(identity).isNotNull();
        assertThat(identity.role()).isEqualTo("ADMIN");
    }

    // ── generateToken(username only) ─────────────────────────────────────────

    @Test
    void generateToken_withUsernameOnly_embedsSubject_andNullClaims() {
        String token = jwtUtil.generateToken("refresh@example.com", 60);

        assertThat(jwtUtil.validateAndExtractUsername(token)).isEqualTo("refresh@example.com");

        JwtUtil.JwtIdentity identity = jwtUtil.validateAndExtractIdentity(token);
        assertThat(identity).isNotNull();
        assertThat(identity.userId()).isNull();
        assertThat(identity.role()).isNull();
    }

    // ── validateAndExtractUsername ────────────────────────────────────────────

    @Test
    void validateAndExtractUsername_returnsEmail_forValidToken() {
        User u = user("user-1", "hello@example.com", Role.USER);
        String token = jwtUtil.generateToken(u, 15);

        assertThat(jwtUtil.validateAndExtractUsername(token)).isEqualTo("hello@example.com");
    }

    @Test
    void validateAndExtractUsername_returnsNull_forTamperedToken() {
        assertThat(jwtUtil.validateAndExtractUsername("not.a.valid.jwt")).isNull();
    }

    @Test
    void validateAndExtractUsername_returnsNull_forExpiredToken() {
        User u = user("user-1", "test@example.com", Role.USER);
        String token = jwtUtil.generateToken(u, -1); // already expired

        assertThat(jwtUtil.validateAndExtractUsername(token)).isNull();
    }

    // ── validateAndExtractIdentity ────────────────────────────────────────────

    @Test
    void validateAndExtractIdentity_returnsAllFields_forValidToken() {
        User u = user("uid-42", "id@example.com", Role.USER);
        String token = jwtUtil.generateToken(u, 15);

        JwtUtil.JwtIdentity identity = jwtUtil.validateAndExtractIdentity(token);

        assertThat(identity).isNotNull();
        assertThat(identity.email()).isEqualTo("id@example.com");
        assertThat(identity.userId()).isEqualTo("uid-42");
        assertThat(identity.role()).isEqualTo("USER");
    }

    @Test
    void validateAndExtractIdentity_returnsNull_forTamperedToken() {
        assertThat(jwtUtil.validateAndExtractIdentity("garbage")).isNull();
    }

    @Test
    void validateAndExtractIdentity_returnsNull_forExpiredToken() {
        User u = user("user-1", "test@example.com", Role.USER);
        String token = jwtUtil.generateToken(u, -1);

        assertThat(jwtUtil.validateAndExtractIdentity(token)).isNull();
    }
}
