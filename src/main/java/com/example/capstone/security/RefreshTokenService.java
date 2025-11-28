package com.example.capstone.security;

import com.example.capstone.dto.login.LoginResponse;
import com.example.capstone.entity.User;
import com.example.capstone.repository.UserRepository;
import com.example.capstone.service.UserService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Objects;

@Service
@AllArgsConstructor
public class RefreshTokenService {
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    public String renewAccessToken(String refreshToken) {
        System.out.println("b1");
        System.out.println("Token received: " + refreshToken);

        try {
            String[] parts = refreshToken.split("\\.");
            String header = new String(Base64.getUrlDecoder().decode(parts[0]));
            System.out.println("JWT header: " + header);

            String email = jwtUtil.extractEmail(refreshToken);
            System.out.println("Extracted email: " + email);
        } catch (Exception e) {
            System.out.println("JWT parse error: " + e.getMessage());
            throw e;
        }

        String email = jwtUtil.extractEmail(refreshToken);
        System.out.println("b2");
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        System.out.println("b3");
        if (!refreshToken.equals(user.getRefreshToken())) {
            throw new RuntimeException("Invalid refresh token");
        }
        System.out.println("b4");
        if (jwtUtil.extractExpiration(refreshToken).before(new Date())) {
            throw new RuntimeException("Refresh token expired, please login again");
        }
        System.out.println("b5");
        return jwtUtil.generateAccessToken(email);
    }

}
