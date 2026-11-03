package com.example.capstone.config;

import com.example.capstone.constant.RepaymentTypeIds;
import com.example.capstone.dto.DebtDTO;
import com.example.capstone.dto.TransactionRequest;
import com.example.capstone.dto.login.LoginResponse;
import com.example.capstone.entity.*;
import com.example.capstone.enums.DebtTxnType;
import com.example.capstone.enums.InterestCalculationType;
import com.example.capstone.enums.InterestInterval;
import com.example.capstone.enums.PaymentInterval;
import com.example.capstone.repository.*;
import com.example.capstone.security.JwtUtil;
import com.example.capstone.service.DebtService;
import com.example.capstone.service.RepaymentPlanService;
import com.example.capstone.service.RepaymentService;
import com.example.capstone.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.*;

import static com.example.capstone.config.GlobalVariables.*;

/**
 * สร้างข้อมูลตัวอย่างสำหรับ demo (ผู้ใช้, หนี้, รายรับ-รายจ่าย, งบประมาณ)
 * เปิดใช้ด้วย app.demo-seed.enabled=true (env: DEMO_SEED_ENABLED=true)
 * ไม่รันตอนสตาร์ท (กัน Render health check หมดเวลา) แต่สร้างตอนมีคนกดปุ่ม Demo ครั้งแรก
 * ผ่าน POST /auth/demo-login ถ้ามีผู้ใช้ demo อยู่แล้วจะข้ามการสร้างข้อมูล
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.demo-seed.enabled", havingValue = "true")
public class DemoDataSeeder {

    private static final ZoneId ZONE = ZoneId.of("Asia/Bangkok");

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final CategoryRepository categoryRepository;
    private final BudgetRepository budgetRepository;
    private final UserSettingRepository userSettingRepository;
    private final DebtTypeRepository debtTypeRepository;
    private final RepaymentTypeRepository repaymentTypeRepository;
    private final DebtRepository debtRepository;
    private final TransactionRepository transactionRepository;
    private final DebtTransactionRepository debtTransactionRepository;
    private final RepaymentStrategyRepository repaymentStrategyRepository;
    private final RepaymentPlanService repaymentPlanService;
    private final DebtService debtService;
    private final RepaymentService repaymentService;
    private final TransactionService transactionService;
    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;

    @Value("${app.demo-seed.email:demo@financecare.com}")
    private String demoEmail;
    @Value("${app.demo-seed.password:Demo@1234}")
    private String demoPassword;

    private final Random random = new Random(42);

    /** สร้างข้อมูล demo ถ้ายังไม่มี แล้วออก token ของผู้ใช้ demo */
    public synchronized LoginResponse loginAsDemoUser() {
        seedIfMissing();
        User user = userRepository.findByEmail(demoEmail).orElseThrow();
        String refreshToken = jwtUtil.generateRefreshToken(demoEmail);
        user.setRefreshToken(refreshToken);
        userRepository.save(user);
        return new LoginResponse(jwtUtil.generateAccessToken(demoEmail), refreshToken);
    }

    private void seedIfMissing() {
        if (userRepository.findByEmail(demoEmail).isPresent()) {
            return;
        }
        log.info("[demo-seed] seeding demo data for {}", demoEmail);

        ensureRepaymentTypes();
        ensureRepaymentStrategies();
        Map<String, DebtType> debtTypes = ensureDebtTypes();
        evictLookupCaches();

        User user = userRepository.save(new User("Demo User", passwordEncoder.encode(demoPassword),
                new GregorianCalendar(1999, Calendar.MARCH, 14).getTime(), demoEmail, true, null));
        UUID userId = user.getUserId();
        String token = jwtUtil.generateAccessToken(demoEmail);

        UserSetting setting = new UserSetting();
        setting.setUserId(userId);
        setting.setSkills("Excel,Customer Service,Photoshop,Content Writing");
        userSettingRepository.save(setting);

        Map<String, Category> categories = seedCategoriesAndBudgets(userId);
        seedDebts(token, debtTypes);
        seedTransactions(userId, categories);
        seedDebtPayments(userId);
        seedRepaymentPlan(userId);

        log.info("[demo-seed] done");
    }

    // ---------- lookup tables ----------

    private void ensureRepaymentTypes() {
        Object[][] types = {
                { RepaymentTypeIds.LUMP_SUM, "Full Payment / Lump Sum", "จ่ายครั้งเดียวทั้งก้อน" },
                { RepaymentTypeIds.EMI, "Installment / EMI", "ผ่อนชำระงวดเท่ากันทุกเดือน" },
                { RepaymentTypeIds.REVOLVING, "Revolving / Credit Line", "วงเงินหมุนเวียน เช่น บัตรเครดิต" },
                { RepaymentTypeIds.BULLET, "Bullet Payment", "จ่ายดอกเบี้ยรายงวด คืนต้นทีเดียวตอนท้าย" },
        };
        for (Object[] t : types) {
            if (!repaymentTypeRepository.existsById((Integer) t[0])) {
                jdbcTemplate.update(
                        "INSERT INTO repayment_type (type_id, type_name, description) VALUES (?, ?, ?)",
                        t[0], t[1], t[2]);
            }
        }
    }

    private Map<String, DebtType> ensureDebtTypes() {
        String[][] wanted = {
                { "บัตรเครดิต", "หนี้บัตรเครดิต" },
                { "สินเชื่อส่วนบุคคล", "สินเชื่อส่วนบุคคลจากธนาคาร" },
                { "สินเชื่อยานพาหนะ", "ผ่อนรถยนต์ / มอเตอร์ไซค์" },
                { "สินเชื่อการศึกษา", "กองทุนเงินให้กู้ยืมเพื่อการศึกษา" },
                { "หนี้นอกระบบ", "ยืมเพื่อน / คนรู้จัก / เงินกู้นอกระบบ" },
                { "ผ่อนสินค้า", "ผ่อนสินค้า 0% / BNPL" },
        };
        Map<String, DebtType> byName = new HashMap<>();
        for (DebtType dt : debtTypeRepository.findAll()) {
            byName.put(dt.getDebtTypeName(), dt);
        }
        for (String[] w : wanted) {
            byName.computeIfAbsent(w[0], n -> debtTypeRepository.save(new DebtType(null, n, w[1])));
        }
        return byName;
    }

    private void evictLookupCaches() {
        try {
            redisTemplate.delete(List.of("debts:types", "repayments:types"));
        } catch (Exception e) {
            log.warn("[demo-seed] could not evict lookup caches: {}", e.getMessage());
        }
    }

    // ---------- categories / budgets ----------

    private Map<String, Category> seedCategoriesAndBudgets(UUID userId) {
        LocalDate today = LocalDate.now(ZONE);
        Object[][] defs = {
                { CATEGORY_SALARY, TYPE_INCOME, 0.0 },
                { CATEGORY_EXTRA_INCOME, TYPE_INCOME, 0.0 },
                { CATEGORY_FOOD, TYPE_EXPENSE, 8000.0 },
                { CATEGORY_TRANSPORT, TYPE_EXPENSE, 3000.0 },
                { CATEGORY_SHOPPING, TYPE_EXPENSE, 4000.0 },
                { CATEGORY_HEALTH, TYPE_EXPENSE, 2000.0 },
                { CATEGORY_ENTERTAINMENT, TYPE_EXPENSE, 2500.0 },
                { CATEGORY_BILLS, TYPE_EXPENSE, 5000.0 },
                { CATEGORY_OTHER, TYPE_EXPENSE, 2000.0 },
                { CATEGORY_SAVING, TYPE_EXPENSE, 5000.0 },
        };
        Map<String, Category> result = new LinkedHashMap<>();
        for (Object[] d : defs) {
            Budget budget = new Budget();
            budget.setUserId(userId);
            budget.setMonth(today.getMonthValue());
            budget.setYear(today.getYear());
            // เงินเดือนตั้งตรงๆ ที่ budget เหมือนที่หน้า Settings ทำ
            budget.setAmount(CATEGORY_SALARY.equals(d[0]) ? 35000.0 : 0.0);
            budget.setLimitBudget((Double) d[2]);
            budget = budgetRepository.save(budget);

            Category category = new Category();
            category.setUserId(userId);
            category.setCategoryName((String) d[0]);
            category.setType((String) d[1]);
            category.setBudgetId(budget.getBudgetId());
            result.put((String) d[0], categoryRepository.save(category));
        }
        return result;
    }

    // ---------- repayment plan ----------

    private void ensureRepaymentStrategies() {
        if (repaymentStrategyRepository.count() > 0) {
            return;
        }
        String[][] strategies = {
                { "Snowball", "ปิดหนี้ก้อนเล็กก่อน เพื่อสร้างกำลังใจ" },
                { "Avalanche", "ปิดหนี้ดอกเบี้ยสูงก่อน เพื่อลดดอกเบี้ยรวม" },
                { "Minimum Only", "จ่ายขั้นต่ำทุกบัญชี" },
                { "Optimal Cost", "ผสมผสานเพื่อให้ต้นทุนรวมต่ำที่สุด" },
        };
        for (String[] st : strategies) {
            RepaymentStrategy strategy = new RepaymentStrategy();
            strategy.setStrategyName(st[0]);
            strategy.setDescription(st[1]);
            strategy.setIsActive(true);
            repaymentStrategyRepository.save(strategy);
        }
    }

    /** ตั้งแผนปลดหนี้แบบ Avalanche งบ 28,000/เดือน (ขั้นต่ำรวม 14,350 และขั้นต่ำปลอดภัยที่ระบบคำนวณ ~27,600) */
    private void seedRepaymentPlan(UUID userId) {
        try {
            RepaymentStrategy avalanche = repaymentStrategyRepository.findAll().stream()
                    .filter(st -> "Avalanche".equalsIgnoreCase(st.getStrategyName()))
                    .findFirst()
                    .orElseThrow();
            repaymentPlanService.changeRepaymentPlan(userId, BigDecimal.valueOf(28000), avalanche.getStrategyId());
        } catch (Exception e) {
            log.error("[demo-seed] repayment plan failed: {}", e.getMessage());
        }
    }

    // ---------- debts ----------

    private record DebtSpec(String name, String type, int repayment, double principal, double outstanding,
                            double rate, double minPay, int dueDay, int priority, int monthsAgo, int monthsLeft,
                            InterestCalculationType calc, InterestInterval interestInterval, boolean informal,
                            double penaltyRate, int grace, double initInterest, double initLateFee) {
    }

    private void seedDebts(String token, Map<String, DebtType> types) {
        LocalDate today = LocalDate.now(ZONE);

        List<DebtSpec> specs = List.of(
                new DebtSpec("บัตรเครดิต KBank", "บัตรเครดิต", RepaymentTypeIds.REVOLVING, 48000, 36500, 16,
                        2000, 25, 1, 14, 24, InterestCalculationType.DAILY_COMPOUND, InterestInterval.YEARLY,
                        false, 20, 3, 0, 0),
                new DebtSpec("สินเชื่อส่วนบุคคล SCB", "สินเชื่อส่วนบุคคล", RepaymentTypeIds.EMI, 120000, 94000, 25,
                        5200, 5, 2, 10, 20, InterestCalculationType.THIRTY_360, InterestInterval.YEARLY,
                        false, 15, 5, 0, 0),
                new DebtSpec("ผ่อนมอเตอร์ไซค์ Honda", "สินเชื่อยานพาหนะ", RepaymentTypeIds.EMI, 65000, 44000, 9,
                        2900, 15, 3, 12, 16, InterestCalculationType.FLAT_RATE, InterestInterval.YEARLY,
                        false, 15, 7, 0, 0),
                new DebtSpec("กยศ. (กองทุนกู้ยืมเพื่อการศึกษา)", "สินเชื่อการศึกษา", RepaymentTypeIds.EMI, 180000,
                        162000, 1, 1500, 28, 5, 18, 108, InterestCalculationType.THIRTY_360,
                        InterestInterval.YEARLY, false, 0, 30, 0, 0),
                new DebtSpec("ผ่อน iPad 0% (BNPL)", "ผ่อนสินค้า", RepaymentTypeIds.EMI, 24000, 18000, 0,
                        2000, 20, 4, 4, 8, InterestCalculationType.THIRTY_360, InterestInterval.YEARLY,
                        false, 0, 3, 0, 0),
                new DebtSpec("ยืมเพื่อน (ดอกเบี้ยนอกระบบ)", "หนี้นอกระบบ", RepaymentTypeIds.BULLET, 15000, 15000, 5,
                        750, 10, 1, 3, 6, InterestCalculationType.DAILY_SIMPLE, InterestInterval.MONTHLY,
                        true, 0, 0, 750, 200));

        for (DebtSpec s : specs) {
            try {
                DebtDTO dto = new DebtDTO();
                dto.setDebtName(s.name());
                dto.setDebtTypeId(types.get(s.type()).getDebtTypeId());
                dto.setRepaymentTypeId(s.repayment());
                dto.setPrincipalAmount(BigDecimal.valueOf(s.principal()));
                dto.setPrincipalOutstanding(BigDecimal.valueOf(s.outstanding()));
                dto.setInterestRate(BigDecimal.valueOf(s.rate()));
                dto.setMinPayment(BigDecimal.valueOf(s.minPay()));
                dto.setDueDay(s.dueDay());
                dto.setPriority(s.priority());
                dto.setStartDate(toDate(today.minusMonths(s.monthsAgo()).withDayOfMonth(Math.min(s.dueDay(), 28))));
                dto.setEndDate(toDate(today.plusMonths(s.monthsLeft())));
                dto.setInterestCalculationType(s.calc());
                dto.setInterestInterval(s.interestInterval());
                dto.setPaymentInterval(PaymentInterval.MONTHLY);
                dto.setIsInformal(s.informal());
                dto.setActive(true);
                dto.setDefaulted(false);
                dto.setPenaltyAnnualRate(BigDecimal.valueOf(s.penaltyRate()));
                dto.setGracePeriodDays(s.grace());
                dto.setPenaltyTriggerDays(s.grace() == 0 ? 1 : s.grace());
                dto.setInitialInterestRemaining(BigDecimal.valueOf(s.initInterest()));
                dto.setInitialLateFeeRemaining(BigDecimal.valueOf(s.initLateFee()));
                dto.setInitialPenaltyRemaining(BigDecimal.ZERO);
                debtService.addDebt(token, dto);
            } catch (Exception e) {
                log.error("[demo-seed] failed to create debt '{}': {}", s.name(), e.getMessage());
            }
        }
    }

    /** จ่ายหนี้ย้อนหลัง 3 เดือนตามวันครบกำหนด (ยกเว้นหนี้นอกระบบ ปล่อยให้ค้างชำระเพื่อโชว์เคสดอกเบี้ย/ค่าปรับ) */
    private void seedDebtPayments(UUID userId) {
        LocalDate today = LocalDate.now(ZONE);
        for (Debt debt : debtRepository.findAllByUserId(userId)) {
            if (Boolean.TRUE.equals(debt.getIsInformal())) {
                continue;
            }
            for (int back = 3; back >= 1; back--) {
                LocalDate due = today.minusMonths(back).withDayOfMonth(Math.min(debt.getDueDay(), 28));
                if (due.isAfter(today) || due.isBefore(toLocal(debt.getStartDate()))) {
                    continue;
                }
                BigDecimal amount = debt.getMinPayment();
                if (debt.getRepaymentType().getRepaymentTypeId() == RepaymentTypeIds.REVOLVING && back == 1) {
                    amount = amount.add(BigDecimal.valueOf(1500)); // จ่ายเกินขั้นต่ำเดือนล่าสุด
                }
                try {
                    // อ่าน debt ใหม่ทุกครั้งเพราะ principalOutstanding ถูกอัปเดตระหว่างจ่าย
                    Debt fresh = debtRepository.findById(debt.getDebtId()).orElse(debt);
                    chargeMonthlyInterest(fresh, due);
                    repaymentService.processDebtPayment(fresh, amount, due, null);
                } catch (Exception e) {
                    log.error("[demo-seed] payment failed for '{}' ({}): {}", debt.getDebtName(), due, e.getMessage());
                }
            }
        }
    }

    /** ดอกเบี้ยรายเดือน = เงินต้นคงเหลือ x อัตราต่อปี / 12 (ปกติ job รายเดือนของระบบเป็นคนสร้าง) */
    private void chargeMonthlyInterest(Debt debt, LocalDate date) {
        BigDecimal rate = debt.getInterestRate();
        BigDecimal outstanding = debt.getPrincipalOutstanding();
        if (rate == null || outstanding == null || rate.signum() <= 0 || outstanding.signum() <= 0) {
            return;
        }
        BigDecimal interest = outstanding.multiply(rate)
                .divide(BigDecimal.valueOf(1200), 2, RoundingMode.HALF_UP);
        if (interest.signum() > 0) {
            debtTransactionRepository.save(DebtTransaction.builder()
                    .debt(debt)
                    .txnType(DebtTxnType.INTEREST_CHARGE)
                    .amount(interest)
                    .txnDate(date)
                    .build());
        }
    }

    // ---------- transactions ----------

    private void seedTransactions(UUID userId, Map<String, Category> cats) {
        LocalDateTime now = LocalDateTime.now(ZONE);
        for (int back = 3; back >= 0; back--) {
            YearMonth ym = YearMonth.from(now).minusMonths(back);
            boolean current = back == 0;
            // เดือนปัจจุบัน: เลื่อนรายการประจำที่ยังไม่ถึงวันที่ให้เกิดขึ้นแล้ว (อย่างช้าเมื่อวาน)
            int lastDay = current ? Math.max(1, now.getDayOfMonth() - 1) : 31;

            // รายรับ: เงินเดือน (budget ตั้งไว้แล้ว จึงบันทึกเฉพาะรายการ) + งานเสริม
            addTxn(userId, cats.get(CATEGORY_SALARY), 35000, at(ym, Math.min(25, lastDay)), "เงินเดือน", now, false);
            if (back != 2) {
                addTxn(userId, cats.get(CATEGORY_EXTRA_INCOME), 2500 + random.nextInt(4) * 1000, at(ym, Math.min(17, lastDay)),
                        pick("รับงานฟรีแลนซ์", "ขายของออนไลน์", "ค่าสอนพิเศษ"), now, current);
            }

            // ค่าใช้จ่ายประจำ
            addTxn(userId, cats.get(CATEGORY_BILLS), 1050 + random.nextInt(300), at(ym, Math.min(8, lastDay)), "ค่าไฟ", now, current);
            addTxn(userId, cats.get(CATEGORY_BILLS), 599, at(ym, Math.min(12, lastDay)), "ค่าอินเทอร์เน็ตบ้าน", now, current);
            addTxn(userId, cats.get(CATEGORY_BILLS), 399, at(ym, Math.min(14, lastDay)), "ค่าโทรศัพท์มือถือ", now, current);
            addTxn(userId, cats.get(CATEGORY_BILLS), 160 + random.nextInt(80), at(ym, Math.min(9, lastDay)), "ค่าน้ำ", now, current);
            addTxn(userId, cats.get(CATEGORY_SAVING), 3000, at(ym, Math.min(26, lastDay)), "ออมเงินประจำเดือน", now, current);

            // ค่าใช้จ่ายผันแปร
            for (int i = 0; i < 22; i++) {
                addTxn(userId, cats.get(CATEGORY_FOOD), 60 + random.nextInt(290), randomDay(ym),
                        pick("ข้าวกลางวัน", "กาแฟ", "ข้าวเย็น", "ชานมไข่มุก", "ซื้อของเข้าบ้าน", "ข้าวมันไก่",
                                "ก๋วยเตี๋ยว", "สั่งฟู้ดเดลิเวอรี่"), now, current);
            }
            for (int i = 0; i < 9; i++) {
                addTxn(userId, cats.get(CATEGORY_TRANSPORT), 30 + random.nextInt(180), randomDay(ym),
                        pick("BTS", "MRT", "แท็กซี่", "วินมอเตอร์ไซค์", "เติมน้ำมัน", "ค่าทางด่วน"), now, current);
            }
            for (int i = 0; i < 3; i++) {
                addTxn(userId, cats.get(CATEGORY_SHOPPING), 350 + random.nextInt(1500), randomDay(ym),
                        pick("เสื้อผ้า", "ของใช้ในบ้าน", "Shopee", "Lazada", "รองเท้า"), now, current);
            }
            addTxn(userId, cats.get(CATEGORY_HEALTH), 200 + random.nextInt(700), randomDay(ym),
                    pick("ค่ายา", "ตรวจสุขภาพ", "ทันตแพทย์", "วิตามิน"), now, current);
            for (int i = 0; i < 3; i++) {
                addTxn(userId, cats.get(CATEGORY_ENTERTAINMENT), 100 + random.nextInt(500), randomDay(ym),
                        pick("ดูหนัง", "Netflix", "Spotify", "เกม", "ไปคาเฟ่กับเพื่อน"), now, current);
            }
            addTxn(userId, cats.get(CATEGORY_OTHER), 100 + random.nextInt(600), randomDay(ym),
                    pick("ทำบุญ", "ของขวัญเพื่อน", "ค่าถ่ายเอกสาร"), now, current);
        }
    }

    /**
     * เดือนปัจจุบันใช้ TransactionService (อัปเดต budget.amount ให้เหมือนใช้งานจริง)
     * เดือนก่อนหน้าบันทึกตรงเป็นประวัติ เพื่อไม่ให้ยอดงบเดือนนี้เพี้ยน
     */
    private void addTxn(UUID userId, Category category, double amount, LocalDateTime when, String desc,
                        LocalDateTime now, boolean viaService) {
        if (when.isAfter(now)) {
            return;
        }
        try {
            if (viaService) {
                TransactionRequest req = new TransactionRequest();
                req.setCategoryId(category.getCategoryId());
                req.setAmount(amount);
                req.setTransactionDate(when);
                req.setDescription(desc);
                transactionService.createTransaction(userId, req);
            } else {
                transactionRepository.save(Transaction.builder()
                        .userId(userId)
                        .category(category)
                        .amount(amount)
                        .transactionDate(when)
                        .description(desc)
                        .build());
            }
        } catch (Exception e) {
            log.error("[demo-seed] transaction '{}' failed: {}", desc, e.getMessage());
        }
    }

    private LocalDateTime at(YearMonth ym, int day) {
        return ym.atDay(Math.min(day, ym.lengthOfMonth())).atTime(LocalTime.of(9, 0));
    }

    private LocalDateTime randomDay(YearMonth ym) {
        return ym.atDay(1 + random.nextInt(ym.lengthOfMonth()))
                .atTime(LocalTime.of(7 + random.nextInt(14), random.nextInt(60)));
    }

    private String pick(String... options) {
        return options[random.nextInt(options.length)];
    }

    private static Date toDate(LocalDate d) {
        return Date.from(d.atStartOfDay(ZONE).toInstant());
    }

    private static LocalDate toLocal(Date d) {
        return d.toInstant().atZone(ZONE).toLocalDate();
    }
}
