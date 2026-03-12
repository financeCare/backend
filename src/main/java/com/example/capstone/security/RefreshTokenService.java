package com.example.capstone.security;

import com.example.capstone.entity.User;
import com.example.capstone.repository.UserRepository;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
@AllArgsConstructor
@Profile("!test")
public class RefreshTokenService {
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    public String renewAccessToken(String refreshToken) {
        String email = jwtUtil.extractEmail(refreshToken);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        if (!refreshToken.equals(user.getRefreshToken())) {
            throw new RuntimeException("Invalid refresh token");
        }
        if (jwtUtil.extractExpiration(refreshToken).before(new Date())) {
            throw new RuntimeException("Refresh token expired, please login again");
        }
        return jwtUtil.generateAccessToken(email);
    }

}
