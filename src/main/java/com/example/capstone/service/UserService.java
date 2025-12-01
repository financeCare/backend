package com.example.capstone.service;

import com.example.capstone.config.LineConfig;
import com.example.capstone.dto.login.IdTokenRequest;
import com.example.capstone.dto.login.LoginResponse;
import com.example.capstone.dto.login.RegisterRequest;
import com.example.capstone.entity.Budget;
import com.example.capstone.entity.Category;
import com.example.capstone.entity.User;
import com.example.capstone.exception.BusinessException;
import com.example.capstone.exception.EmailAlreadyExistsException;
import com.example.capstone.repository.BudgetRepository;
import com.example.capstone.repository.CategoryRepository;
import com.example.capstone.repository.UserRepository;
import com.example.capstone.security.JwtUtil;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.*;

import static com.example.capstone.config.GlobalVariables.*;

@Service
@RequiredArgsConstructor
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;
    private final JavaMailSender mailSender;
    private final TemplateService templateService;
    private final CategoryRepository categoryRepository;
    private final BudgetRepository budgetRepository;

    @Value("${app.mail.enabled}")
    private boolean mailEnabled;
    private final LineConfig lineConfig;
    private final RestTemplate restTemplate = new RestTemplate();
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

    private Map<String, Object> getLineUserInfo(String idToken) {
        String url = "https://api.line.me/oauth2/v2.1/verify";
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("id_token", idToken);
        params.add("client_id", lineConfig.getChannelId());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<MultiValueMap<String, String>> request =
                new HttpEntity<>(params, headers);
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                request,
                new ParameterizedTypeReference<Map<String, Object>>() {
                }
        );
        Map<String, Object> body = response.getBody();
        if (body == null) {
            throw new RuntimeException("LINE verify: empty response");
        }
        return body;
    }

    public void registerUser(RegisterRequest request) {
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
                        false, null
                )
        );
            User newUser = userRepository.findByEmail(request.getEmail()).get();
            createDefaultCategoriesForUser(newUser.getUserId());
    }

    public LoginResponse loginWithLine(IdTokenRequest idTokenRequest) {
        Map<String, Object> userDetail = getLineUserInfo(idTokenRequest.getIdToken());
        String email = userDetail.get("email").toString();
        String name = userDetail.get("name").toString();
        String accessToken = jwtUtil.generateAccessToken(email);
        String refreshToken = jwtUtil.generateRefreshToken(email);
        Optional<User> userOpt = userRepository.findByEmail(email);
        User user;
        if (userOpt.isEmpty()) {
            user = new User(
                    name,
                    null,
                    null,
                    email,
                    true,
                    refreshToken
            );
        } else {
            user = userOpt.get();
            user.setRefreshToken(refreshToken);
        }
        userRepository.save(user);
        if (userOpt.isEmpty()) {
            User newUser = userRepository.findByEmail(email).get();
            createDefaultCategoriesForUser(newUser.getUserId());
        }
        return new LoginResponse(accessToken, refreshToken);
    }

    public LoginResponse loginWithGoogle(IdTokenRequest idTokenString) {
        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    JSON_FACTORY
            ).build();
            GoogleIdToken idToken = verifier.verify(idTokenString.getIdToken());
            if (idToken == null) {
                throw new BusinessException("Invalid ID token", HttpStatus.UNAUTHORIZED);
            }
            GoogleIdToken.Payload payload = idToken.getPayload();
            String email = payload.getEmail();
            String fullName = (String) payload.get("name");
            Optional<User> userOpt = userRepository.findByEmail(email);
            String accessToken = jwtUtil.generateAccessToken(email);
            String refreshToken = jwtUtil.generateRefreshToken(email);
            User user;
            if (userOpt.isEmpty()) {
                user = new User(
                        fullName,
                        null,
                        null,
                        email,
                        true,
                        refreshToken
                );
            } else {
                user = userOpt.get();
                user.setRefreshToken(refreshToken);
            }
            userRepository.save(user);
            if (userOpt.isEmpty()) {
                User newUser = userRepository.findByEmail(email).get();
                createDefaultCategoriesForUser(newUser.getUserId());
            }
            return new LoginResponse(accessToken, refreshToken);
        } catch (Exception e) {
            throw new BusinessException("Google login failed: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public void createDefaultCategoriesForUser(UUID userId) {
                createCategoryForUser(userId, CATEGORY_SALARY, TYPE_INCOME);
                createCategoryForUser(userId, CATEGORY_FOOD, TYPE_EXPENSE);
                createCategoryForUser(userId, CATEGORY_TRANSPORT, TYPE_EXPENSE);
                createCategoryForUser(userId, CATEGORY_HEALTH, TYPE_EXPENSE);
                createCategoryForUser(userId, CATEGORY_SHOPPING, TYPE_EXPENSE);
    }

    public void createCategoryForUser(UUID userId, String categoryName, String type) {
        Budget budget = new Budget();
        budget.setUserId(userId);
        budget.setAmount(DEFAULT_BUDGET_AMOUNT);
        if( type.equals(TYPE_EXPENSE)){
            budget.setLimitBudget(DEFAULT_LIMIT_BUDGET);
        } else {
            budget.setLimitBudget(null);
        }
        Budget savedBudget = budgetRepository.save(budget);
        Category category = Category.builder()
                .userId(userId)
                .categoryName(categoryName)
                .type(type)
                .budgetId(savedBudget.getBudgetId())
                .build();
         categoryRepository.save(category);
    }


    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new UsernameNotFoundException("User with email " + email + " not found")
                );
        String password = Optional.ofNullable(user.getPasswordHash()).orElse("{noop}dummy");
        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                password,
                Collections.emptyList()
        );
    }

    public UUID extractUserIdFromToken(String token) {
        String email = jwtUtil.extractEmail(token);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new UsernameNotFoundException("User with email " + email + " not found")
                );
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(jwtUtil.getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
        if (user.getEmailConfirm() == false) {
            throw new BusinessException("Email not verified", HttpStatus.FORBIDDEN);
        }if ("refresh".equals(claims.get("type"))) {
            throw new RuntimeException("Refresh token cannot be used to access resources");
        }
        return user.getUserId();
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
