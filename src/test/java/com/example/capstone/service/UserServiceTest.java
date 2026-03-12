package com.example.capstone.service;

import com.example.capstone.dto.login.RegisterRequest;
import com.example.capstone.entity.User;
import com.example.capstone.exception.EmailAlreadyExistsException;
import com.example.capstone.repository.*;
import com.example.capstone.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private BCryptPasswordEncoder passwordEncoder;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private JavaMailSender mailSender;
    @Mock
    private TemplateService templateService;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private BudgetRepository budgetRepository;
    @Mock
    private UserSettingRepository userSettingRepository;
    @Mock
    private RepaymentPlanRepository repaymentPlanRepository;
    @Mock
    private UserDeviceRepository userDeviceRepository;
    @Mock
    private ValueOperations<String, String> valueOps;

    @InjectMocks
    private UserService userService;

    private RegisterRequest registerRequest;

    @BeforeEach
    void setUp() {
        registerRequest = new RegisterRequest();
        registerRequest.setEmail("test@example.com");
        registerRequest.setPassword("password123");
        registerRequest.setUsername("testuser");
    }

    @Test
    void testRegisterUser_Success() {
        User user = new User();
        user.setUserId(UUID.randomUUID());
        user.setEmail(registerRequest.getEmail());

        when(userRepository.findByEmail(registerRequest.getEmail()))
            .thenReturn(Optional.empty()) // First check in registerUser
            .thenReturn(Optional.of(user)); // Second check after save
        
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenReturn(user);

        // Mock for createDefaultCategoriesForUser
        com.example.capstone.entity.Budget mockBudget = new com.example.capstone.entity.Budget();
        mockBudget.setBudgetId(UUID.randomUUID());
        when(budgetRepository.save(any())).thenReturn(mockBudget);
        
        com.example.capstone.entity.Category mockCategory = new com.example.capstone.entity.Category();
        mockCategory.setCategoryId(1);
        when(categoryRepository.save(any())).thenReturn(mockCategory);

        userService.registerUser(registerRequest);

        verify(userRepository, atLeastOnce()).save(any(User.class));
    }

    @Test
    void testRegisterUser_EmailAlreadyExists() {
        when(userRepository.findByEmail(registerRequest.getEmail())).thenReturn(Optional.of(new User()));

        assertThrows(EmailAlreadyExistsException.class, () -> {
            userService.registerUser(registerRequest);
        });

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void testVerifyOtp_Success() {
        String email = "test@example.com";
        String otp = "123456";
        String key = "otp:" + email;

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(key)).thenReturn(otp);

        User user = new User();
        user.setEmail(email);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        boolean result = userService.verifyOtp(email, otp);

        assertTrue(result);
        assertTrue(user.getEmailConfirm());
        verify(userRepository).save(user);
        verify(redisTemplate).delete(key);
    }

    @Test
    void testVerifyOtp_Failure() {
        String email = "test@example.com";
        String otp = "123456";
        String key = "otp:" + email;

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(key)).thenReturn("654321"); // Different OTP

        boolean result = userService.verifyOtp(email, otp);

        assertFalse(result);
        verify(userRepository, never()).save(any(User.class));
    }
}
