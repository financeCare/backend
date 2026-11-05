package com.example.capstone.security;

import com.example.capstone.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminAccessTest {

    private final JwtUtil jwtUtil = mock(JwtUtil.class);

    private AdminAccess accessFor(String adminEmails, String callerEmail) {
        when(jwtUtil.extractEmail("t")).thenReturn(callerEmail);
        return new AdminAccess(jwtUtil, adminEmails);
    }

    @Test
    void listedAdministratorIsAllowed_caseAndSpacesIgnored() {
        AdminAccess access = accessFor(" Admin@Example.com , other@example.com", "admin@example.com");

        assertDoesNotThrow(() -> access.requireAdmin("Bearer t"));
    }

    @Test
    void anyoneElseIsForbidden() {
        AdminAccess access = accessFor("admin@example.com", "user@example.com");

        BusinessException ex = assertThrows(BusinessException.class, () -> access.requireAdmin("Bearer t"));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    @Test
    void withNoAdministratorsConfiguredNobodyIsAllowed() {
        AdminAccess access = accessFor("", "admin@example.com");

        assertThrows(BusinessException.class, () -> access.requireAdmin("Bearer t"));
    }

    @Test
    void aTokenWithoutAnEmailIsForbidden() {
        AdminAccess access = accessFor("admin@example.com", null);

        assertThrows(BusinessException.class, () -> access.requireAdmin("Bearer t"));
    }
}
