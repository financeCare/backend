package com.example.capstone.controller;

import com.example.capstone.dto.login.IdTokenRequest;
import com.example.capstone.dto.login.LoginRequest;
import com.example.capstone.dto.login.LoginResponse;
import com.example.capstone.dto.login.RegisterRequest;
import com.example.capstone.dto.VerifyOtpDto;
import com.example.capstone.config.DemoDataSeeder;
import com.example.capstone.entity.User;
import com.example.capstone.repository.UserRepository;
import com.example.capstone.security.RefreshTokenService;
import com.example.capstone.service.UserService;
import com.example.capstone.security.JwtUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {
    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UserService userService;
    private final RefreshTokenService refreshTokenService;
    private final UserRepository userRepository;
    private final ObjectProvider<DemoDataSeeder> demoSeeder;

    @PostMapping("/send-otp/{email}")
    public ResponseEntity<String> sendOtp(@PathVariable("email") String email) {
        try {
            userService.sendOtp(email);
            return ResponseEntity.ok("OTP sent successfully");
        } catch (Exception e) {
            log.error("Error in sendOtp: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error in sendOtp: " + e.getMessage());
        }
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<String> verifyOtp(@RequestBody VerifyOtpDto request) {
        try {
            boolean isValid = userService.verifyOtp(request.getEmail(), request.getOtp());
            if (isValid) {
                return ResponseEntity.ok("OTP verified successfully");
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid or expired OTP");
            }
        } catch (Exception e) {
            log.error("Error in verifyOtp: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    /** เข้าสู่ระบบด้วยบัญชี demo (พร้อมข้อมูลตัวอย่าง) เปิดใช้เมื่อ app.demo-seed.enabled=true เท่านั้น */
    @PostMapping("/demo-login")
    public ResponseEntity<Object> demoLogin() {
        DemoDataSeeder seeder = demoSeeder.getIfAvailable();
        if (seeder == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Demo mode is disabled");
        }
        try {
            return ResponseEntity.ok(seeder.loginAsDemoUser());
        } catch (Exception e) {
            log.error("Error in demoLogin: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Demo login failed: " + e.getMessage());
        }
    }

    @PostMapping("/login")
    public ResponseEntity<Object> login(@Valid @RequestBody LoginRequest request) {
        try {
            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword());
            authenticationManager.authenticate(authToken);

            String token = jwtUtil.generateAccessToken(request.getEmail());
            String refreshToken = jwtUtil.generateRefreshToken(request.getEmail());
            User user = userRepository.findByEmail(request.getEmail())
                    .orElseThrow(() -> new UsernameNotFoundException("User not found"));
            user.setRefreshToken(refreshToken);
            userRepository.save(user);
            return ResponseEntity.ok(new LoginResponse(token, refreshToken));
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid password or email");
        } catch (UsernameNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found");
        } catch (Exception e) {
            log.error("Login failed: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Login failed");
        }
    }

    @PostMapping("/login/line")
    public ResponseEntity<LoginResponse> loginWithLine(@RequestBody IdTokenRequest request) {
        LoginResponse token = userService.loginWithLine(request);
        return ResponseEntity.ok(new LoginResponse(token.getAccessToken(), token.getRefreshToken()));
    }

    @PostMapping("/login/google")
    public ResponseEntity<LoginResponse> loginWithGoogle(@RequestBody IdTokenRequest request) {
        LoginResponse token = userService.loginWithGoogle(request);
        return ResponseEntity.ok(new LoginResponse(token.getAccessToken(), token.getRefreshToken()));
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<Object> refreshToken(@RequestHeader("Authorization") String authorizationHeader) {
        try {
            String tokenStr = authorizationHeader.replace(BEARER_PREFIX, "");
            String token = refreshTokenService.renewAccessToken(tokenStr);
            return ResponseEntity.ok(new LoginResponse(token, tokenStr));
        } catch (Exception e) {
            log.error("Error refreshing token: ", e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
        }
    }

    @PostMapping("/register")
    public ResponseEntity<String> register(@Valid @RequestBody RegisterRequest request) {
        try {
            userService.registerUser(request);
            return ResponseEntity.status(HttpStatus.CREATED).body("user created");
        } catch (Exception e) {
            log.error("Error in register: ", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Error in register: " + e.getMessage());
        }
    }
}
