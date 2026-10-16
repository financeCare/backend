package com.example.capstone.service;

import com.example.capstone.config.LineConfig;
import com.example.capstone.dto.DeviceListDto;
import com.example.capstone.dto.UserSettingDto;
import com.example.capstone.dto.UserSettingResponseDto;
import com.example.capstone.dto.login.IdTokenRequest;
import com.example.capstone.dto.login.LoginResponse;
import com.example.capstone.dto.login.RegisterRequest;
import com.example.capstone.entity.*;
import com.example.capstone.exception.BusinessException;
import com.example.capstone.exception.EmailAlreadyExistsException;
import com.example.capstone.repository.*;
import com.example.capstone.security.JwtUtil;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.mail.internet.MimeMessage;
import jakarta.transaction.Transactional;
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

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.*;

import static com.example.capstone.config.GlobalVariables.*;

@Service
@RequiredArgsConstructor
public class UserService implements UserDetailsService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String USER_NOT_FOUND_PREFIX = "User with email ";
    private static final String NOT_FOUND_SUFFIX = " not found";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

    private static final String[] DEFAULT_INCOMES = { CATEGORY_SALARY, CATEGORY_EXTRA_INCOME };
    private static final String[] DEFAULT_EXPENSES = {
        CATEGORY_FOOD, CATEGORY_TRANSPORT, CATEGORY_SHOPPING, CATEGORY_HEALTH,
        CATEGORY_ENTERTAINMENT, CATEGORY_BILLS, CATEGORY_OTHER, CATEGORY_SAVING
    };

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;
    private final JavaMailSender mailSender;
    private final TemplateService templateService;
    private final CategoryRepository categoryRepository;
    private final BudgetRepository budgetRepository;
    private final UserSettingRepository userSettingRepository;
    private final RepaymentPlanRepository repaymentPlanRepository;
    private final UserDeviceRepository userDeviceRepository;

    @Value("${app.mail.enabled}")
    private boolean mailEnabled;
    @Value("${app.google.client-id}")
    private String googleClientId;
    private final LineConfig lineConfig;
    private final RestTemplate restTemplate = new RestTemplate();

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
            throw new BusinessException("LINE verify: empty response", HttpStatus.BAD_REQUEST);
        }
        return body;
    }

    public void registerUser(RegisterRequest request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new EmailAlreadyExistsException("Email already exists: " + request.getEmail());
        }
        String encodedPassword = passwordEncoder.encode(request.getPassword());
        User savedUser = userRepository.save(
                new User(
                        request.getUsername(),
                        encodedPassword,
                        request.getDob(),
                        request.getEmail(),
                        false, null
                )
        );
        createDefaultCategoriesForUser(savedUser.getUserId());
        createUserSettingIfNotExists(savedUser.getUserId());
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
            User newUser = userRepository.findByEmail(email).orElseThrow(() -> new BusinessException("can't find this user email", HttpStatus.BAD_REQUEST));
            createDefaultCategoriesForUser(newUser.getUserId());
        } else if (categoryRepository.findByUserId(user.getUserId()).isEmpty()) {
            createDefaultCategoriesForUser(user.getUserId());
        }

        createUserSettingIfNotExists(user.getUserId());
        return new LoginResponse(accessToken, refreshToken);
    }

    public LoginResponse loginWithGoogle(IdTokenRequest idTokenRequest) {
        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(GoogleNetHttpTransport.newTrustedTransport(), JSON_FACTORY)
                    .setAudience(Collections.singletonList(googleClientId))
                    .build();

            GoogleIdToken idToken = verifier.verify(idTokenRequest.getIdToken());
            if (idToken == null) {
                throw new BusinessException("Invalid ID token.", HttpStatus.UNAUTHORIZED);
            }
            GoogleIdToken.Payload payload = idToken.getPayload();
            String email = payload.getEmail();
            String name = (String) payload.get("name");
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
                User newUser = userRepository.findByEmail(email).orElseThrow(() -> new BusinessException("can't find this user email", HttpStatus.BAD_REQUEST));
                createDefaultCategoriesForUser(newUser.getUserId());
            } else if (categoryRepository.findByUserId(user.getUserId()).isEmpty()) {
                createDefaultCategoriesForUser(user.getUserId());
            }
            createUserSettingIfNotExists(user.getUserId());
            return new LoginResponse(accessToken, refreshToken);
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            throw new BusinessException("Google login failed: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Transactional
    public void createDefaultCategoriesForUser(UUID userId) {
        for (String categoryName : DEFAULT_INCOMES) {
            createCategoryWithBudget(userId, categoryName, TYPE_INCOME);
        }

        for (String categoryName : DEFAULT_EXPENSES) {
            createCategoryWithBudget(userId, categoryName, TYPE_EXPENSE);
        }
    }

    private void createCategoryWithBudget(UUID userId, String categoryName, String type) {
        Budget budget = new Budget();
        budget.setUserId(userId);
        budget.setAmount(0.0);
        budget.setLimitBudget(1000.0);
        Budget savedBudget = budgetRepository.save(budget);

        Category category = new Category();
        category.setUserId(userId);
        category.setCategoryName(categoryName);
        category.setType(type);
        category.setBudgetId(savedBudget.getBudgetId());
        categoryRepository.save(category);
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException(USER_NOT_FOUND_PREFIX + email + NOT_FOUND_SUFFIX));
        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPasswordHash(),
                Collections.emptyList()
        );
    }

    public UUID extractUserIdFromToken(String token) {
        String email = jwtUtil.extractEmail(token);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new UsernameNotFoundException(USER_NOT_FOUND_PREFIX + email + NOT_FOUND_SUFFIX)
                );
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(jwtUtil.getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
        if (!Boolean.TRUE.equals(user.getEmailConfirm())) {
            throw new BusinessException("Email not verified", "EMAIL_NOT_VERIFIED", HttpStatus.FORBIDDEN);
        }
        if ("refresh".equals(claims.get("type"))) {
            throw new BusinessException("Refresh token cannot be used to access resources", HttpStatus.FORBIDDEN);
        }
        return user.getUserId();
    }

    private String generateOtp() {
        return String.format("%06d", SECURE_RANDOM.nextInt(1000000));
    }

    public void sendOtp(String email) {
        String otp = generateOtp();
        redisTemplate.opsForValue().set("otp:" + email, otp, Duration.ofMinutes(5));
        if (!mailEnabled) {
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
        } catch (Exception e) {
            throw new BusinessException(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public boolean verifyOtp(String email, String otp) {
        String key = "otp:" + email;
        String storedOtp = redisTemplate.opsForValue().get(key);
        if (storedOtp == null || !storedOtp.equals(otp)) {
            return false;
        }
        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new BusinessException(USER_NOT_FOUND_PREFIX + email + NOT_FOUND_SUFFIX, "USER_NOT_FOUND", HttpStatus.NOT_FOUND)
                );
        user.setEmailConfirm(true);
        userRepository.save(user);
        redisTemplate.delete(key);
        return true;
    }

    public void createUserSettingIfNotExists(UUID userId) {
        UserSetting userSettingOpt = userSettingRepository.findByUserId(userId);
        if (userSettingOpt == null) {
            UserSetting userSetting = new UserSetting();
            userSetting.setUserId(userId);
            userSettingRepository.save(userSetting);
        }
    }

    public UserSettingResponseDto getUserSetting(String token) {
        UUID userId = extractUserIdFromToken(token);
        List<UserDevice> devices = userDeviceRepository.findAllByUserIdAndActiveTrue(userId);
        return new UserSettingResponseDto(userSettingRepository.findByUserId(userId), devices.stream()
                .map(ud -> new DeviceListDto(
                        ud.getDeviceId(),
                        ud.getDeviceName(),
                        ud.getPlatform(),
                        ud.getLastSeen()
                ))
                .toList());
    }

    public UserSetting setUserSetting(String token, UserSettingDto newSettings) {
        UUID userId = extractUserIdFromToken(token);
        UserSetting userSetting = userSettingRepository.findByUserId(userId);
        if (userSetting == null) {
            throw new BusinessException("User setting not found", "USER_SETTING_NOT_FOUND", HttpStatus.NOT_FOUND);
        }
        userSetting.setNotificationsEnabled(newSettings.isNotificationsEnabled());
        userSetting.setDefaultNotifyTime(newSettings.getDefaultNotifyTime());
        userSetting.setDefaultRemindDaysBefore(newSettings.getNotifyDueDaysBefore());
        userSetting.setSkills(newSettings.getSkills());
        RepaymentPlan repaymentPlan = repaymentPlanRepository.findByUserId(userId);
        if (repaymentPlan != null) {
            repaymentPlan.setMonthlyBudget(BigDecimal.valueOf(newSettings.getMonthlyRepaymentBudget()));
            repaymentPlanRepository.save(repaymentPlan);
        } else {
            RepaymentPlan plan = new RepaymentPlan();
            plan.setPlanId(UUID.randomUUID());
            plan.setUserId(userId);
            plan.setMonthlyBudget(BigDecimal.valueOf(newSettings.getMonthlyRepaymentBudget()));
            repaymentPlanRepository.save(plan);
        }
        userSettingRepository.save(userSetting);
        return userSetting;
    }
}
