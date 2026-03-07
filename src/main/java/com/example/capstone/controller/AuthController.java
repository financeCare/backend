package com.example.capstone.controller;

import com.example.capstone.dto.login.IdTokenRequest;
import com.example.capstone.dto.login.LoginRequest;
import com.example.capstone.dto.login.LoginResponse;
import com.example.capstone.dto.login.RegisterRequest;
import com.example.capstone.dto.verifyOTPDTO;
import com.example.capstone.entity.User;
import com.example.capstone.repository.UserRepository;
import com.example.capstone.security.RefreshTokenService;
import com.example.capstone.service.UserService;
import com.example.capstone.security.JwtUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

@CrossOrigin
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UserService userService;
    private final RefreshTokenService refreshTokenService;
    private final UserRepository userRepository;
    @PostMapping("/send-otp/{email}")
    public ResponseEntity<?> sendOtp(@PathVariable String email) {
        try {
            userService.sendOtp(email);
            return ResponseEntity.ok("OTP sent successfully");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(@RequestBody verifyOTPDTO request) {
        try {
            boolean isValid = userService.verifyOtp(request.getEmail(), request.getOtp());
            if (isValid) {
                return ResponseEntity.ok("OTP verified successfully");
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid or expired OTP");
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
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
            return ResponseEntity.ok(new LoginResponse(token,refreshToken));
        }catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid password or email");
        } catch (UsernameNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Login failed");
        }
    }

    @PostMapping("/login/line")
    public ResponseEntity<?> loginWithLine(@RequestBody IdTokenRequest request) {
        LoginResponse token = userService.loginWithLine(request);
        return ResponseEntity.ok(new LoginResponse(token.getAccessToken(),token.getRefreshToken()));
    }

    @PostMapping("/login/google")
    public ResponseEntity<?> loginWithGoogle(@RequestBody IdTokenRequest request) {
        LoginResponse token = userService.loginWithGoogle(request);
        return ResponseEntity.ok(new LoginResponse(token.getAccessToken(),token.getRefreshToken()));
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<?> refreshToken(@RequestHeader("Authorization") String authorizationHeader) {
        try {
            String tokenStr = authorizationHeader.replace("Bearer ", "");
            String token = refreshTokenService.renewAccessToken(tokenStr);
            return ResponseEntity.ok(new LoginResponse(token, tokenStr));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request){
        try {
            userService.registerUser(request);
            return ResponseEntity.status(HttpStatus.CREATED).body("user created");
        } catch (Exception e){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }
}
