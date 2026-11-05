package com.example.capstone.security;

import com.example.capstone.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The reference data shared by all users (debt types, repayment types, repayment strategies) may only be changed by
 * an administrator. The app has no role model, so administrators are listed by e-mail in {@code app.admin.emails}
 * (env {@code ADMIN_EMAILS}, comma separated). The default is empty: nobody can change the shared data through the API.
 */
@Component
public class AdminAccess {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;
    private final Set<String> adminEmails;

    public AdminAccess(JwtUtil jwtUtil, @Value("${app.admin.emails:}") String adminEmails) {
        this.jwtUtil = jwtUtil;
        this.adminEmails = Arrays.stream(adminEmails.split(","))
                .map(e -> e.trim().toLowerCase(Locale.ROOT))
                .filter(e -> !e.isEmpty())
                .collect(Collectors.toSet());
    }

    public void requireAdmin(String authorizationHeader) {
        String token = authorizationHeader.replace(BEARER_PREFIX, "");
        String email = jwtUtil.extractEmail(token);
        if (email == null || !adminEmails.contains(email.toLowerCase(Locale.ROOT))) {
            throw new BusinessException("Forbidden", HttpStatus.FORBIDDEN);
        }
    }
}
