package com.example.capstone;

import com.example.capstone.config.LineConfig;
import com.example.capstone.config.OTPConfig;
import com.example.capstone.security.JwtUtil;
import com.example.capstone.security.RefreshTokenService;
import com.google.firebase.FirebaseApp;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class CapstoneApplicationTests {

	@MockitoBean
	private StringRedisTemplate redisTemplate;

	@MockitoBean
	private JavaMailSender mailSender;

	@MockitoBean
	private JwtUtil jwtUtil;

	@MockitoBean
	private LineConfig lineConfig;

	@MockitoBean
	private RefreshTokenService refreshTokenService;

	@MockitoBean
	private OTPConfig otpConfig;

	@MockitoBean
	private FirebaseApp firebaseApp;

	@Test
	void contextLoads() {
	}

}
