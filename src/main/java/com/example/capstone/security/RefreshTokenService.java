package com.example.capstone.security;

import com.example.capstone.entity.User;
import com.example.capstone.exception.BusinessException;
import com.example.capstone.repository.UserRepository;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
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
                .orElseThrow(() -> new BusinessException("User not found", "USER_NOT_FOUND", HttpStatus.NOT_FOUND));
        if (!refreshToken.equals(user.getRefreshToken())) {
            throw new BusinessException("Invalid refresh token", "INVALID_REFRESH_TOKEN", HttpStatus.UNAUTHORIZED);
        }
        if (jwtUtil.extractExpiration(refreshToken).before(new Date())) {
            throw new BusinessException("Refresh token expired, please login again", "REFRESH_TOKEN_EXPIRED", HttpStatus.UNAUTHORIZED);
        }
        return jwtUtil.generateAccessToken(email);
    }

}
