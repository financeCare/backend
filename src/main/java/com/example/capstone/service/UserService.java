package com.example.capstone.service;

import com.example.capstone.dto.login.RegisterRequest;
import com.example.capstone.entity.User;
import com.example.capstone.exception.BusinessException;
import com.example.capstone.exception.EmailAlreadyExistsException;
import com.example.capstone.repository.UserRepository;
import com.example.capstone.security.JwtUtil;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.Random;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;

@Service
@RequiredArgsConstructor
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;
    private final JavaMailSender mailSender;
    private final TemplateService templateService;

    @Value("${app.mail.enabled}")
    private boolean mailEnabled;

    public void registerUser(RegisterRequest request) throws MessagingException, IOException {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new EmailAlreadyExistsException("Email already exists: " + request.getEmail());
        }

        String encodedPassword = passwordEncoder.encode(request.getPassword());

        sendOtp(request.getEmail());
        userRepository.save(
                new User(
                        request.getUsername(),
                        encodedPassword,
                        request.getDob(),
                        request.getEmail(),
                        false
                )
        );
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new UsernameNotFoundException("User with email " + email + " not found")
                );

        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPasswordHash(),
                Collections.emptyList()
        );
    }

    public UUID extractUserIdFromToken(String token){
        String email = jwtUtil.extractEmail(token);
        return userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new UsernameNotFoundException("User with email " + email + " not found")
                ).getUserId();
    }

    private String generateOTP() {
        return String.format("%06d", new Random().nextInt(999999));
    }

    public void sendOtp(String email) {
        String otp = generateOTP();
        redisTemplate.opsForValue().set("otp:" + email, otp, Duration.ofMinutes(5));
        if (!mailEnabled) {
            System.out.println("DEV MODE OTP for " + email + " = " + otp);
            return;
        }
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setTo(email);
            helper.setSubject("Your Verification Code");
            String html = templateService.loadTemplate("template/email-otp.html")
                    .replace("{{otp}}", otp);
            helper.setText(html, true);
            mailSender.send(mimeMessage);
        } catch (Exception e){
            throw new BusinessException(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR );
        }
    }

    public boolean verifyOtp(String email, String otp) {
        String key = "otp:" + email;

        String storedOtp = redisTemplate.opsForValue().get(key);

        if (storedOtp == null) {
            return false;
        }

        if (!storedOtp.equals(otp)) {
            return false;
        }

        redisTemplate.delete(key);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new UsernameNotFoundException("User with email " + email + " not found")
                );
        user.setEmailConfirm(true);
        userRepository.save(user);
        return true;
    }

}
