package com.example.capstone.service;

import com.example.capstone.dto.BudgetOverviewDto;
import com.example.capstone.dto.DebtDTO;
import com.example.capstone.dto.DebtPriorityResponseDTO;
import com.example.capstone.dto.DebtPriorityUpdateRequestDTO;
import com.example.capstone.dto.DebtSummaryDTO;
import com.example.capstone.dto.DebtTransactionResponseDTO;
import com.example.capstone.dto.OverviewGraphDTO;
import com.example.capstone.entity.Category;
import com.example.capstone.entity.Debt;
import com.example.capstone.entity.DebtTransaction;
import com.example.capstone.entity.DebtType;
import com.example.capstone.entity.RepaymentType;
import com.example.capstone.enums.DebtTxnType;
import com.example.capstone.enums.InterestCalculationType;
import com.example.capstone.exception.BusinessException;
import com.example.capstone.repository.CategoryRepository;
import com.example.capstone.repository.DebtRepository;
import com.example.capstone.repository.DebtTransactionRepository;
import com.example.capstone.repository.DebtTypeRepository;
import com.example.capstone.repository.RepaymentTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Debt create/update/delete, priorities, summary, history and the overview graph. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DebtServiceBehaviourTest {

    @Mock
    private DebtTypeRepository debtTypeRepository;
    @Mock
    private DebtRepository debtRepository;
    @Mock
    private RepaymentTypeRepository repaymentTypeRepository;
    @Mock
    private UserService userService;
    @Mock
    private BudgetService budgetService;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private DebtTransactionRepository debtTransactionRepository;
    @Mock
    private RepaymentPlanService repaymentPlanService;

    @InjectMocks
    private DebtService service;

    private UUID userId;
    private final String token = "token";

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        when(userService.extractUserIdFromToken(token)).thenReturn(userId);
        when(repaymentTypeRepository.findById(1)).thenReturn(Optional.of(new RepaymentType()));
        when(debtTypeRepository.findById(2)).thenReturn(Optional.of(new DebtType()));
        when(debtRepository.save(any(Debt.class))).thenAnswer(i -> i.getArgument(0));
    }

    private DebtDTO dto(String rate) {
        DebtDTO dto = new DebtDTO();
        dto.setDebtName("Card");
        dto.setPrincipalAmount(new BigDecimal("10000"));
        dto.setInterestRate(new BigDecimal(rate));
        dto.setRepaymentTypeId(1);
        dto.setDebtTypeId(2);
        dto.setMinPayment(new BigDecimal("500"));
        dto.setDueDay(5);
        dto.setStartDate(new Date());
        dto.setInterestCalculationType(InterestCalculationType.THIRTY_360);
        return dto;
    }

    private Debt ownDebt(UUID id) {
        Debt d = new Debt();
        d.setDebtId(id);
        d.setUserId(userId);
        d.setDebtName("old name");
        d.setActive(false);
        when(debtRepository.findByDebtIdAndUserId(id, userId)).thenReturn(Optional.of(d));
        return d;
    }

    // ---------- create ----------

    @Test
    void addDebt_savesNotifiesAndEvictsTheCache_andDefaultsTheOptionalFields() {
        DebtDTO dto = dto("12");

        Debt debt = service.addDebt(token, dto);

        assertEquals(userId, debt.getUserId());
        assertEquals(new BigDecimal("10000"), debt.getPrincipalOutstanding());    // defaults to the principal
        assertTrue(debt.getActive());
        assertEquals(BigDecimal.ZERO, debt.getInitialInterestRemaining());
        verify(notificationService).createNotificationRuleForDebt(userId, "Card", new BigDecimal("500"), debt.getDebtId());
        verify(repaymentPlanService).evictUserCache(userId);
        verify(debtTransactionRepository, never()).save(any());                    // nothing owed on day one
    }

    @Test
    void addDebt_withOutstandingChargesAlreadyOwed_createsOneTransactionForEach() {
        DebtDTO dto = dto("12");
        dto.setPrincipalOutstanding(new BigDecimal("8000"));
        dto.setInitialInterestRemaining(new BigDecimal("100.555"));
        dto.setInitialLateFeeRemaining(new BigDecimal("50"));
        dto.setInitialPenaltyRemaining(new BigDecimal("25"));

        Debt debt = service.addDebt(token, dto);

        assertEquals(new BigDecimal("8000"), debt.getPrincipalOutstanding());
        ArgumentCaptor<DebtTransaction> txns = ArgumentCaptor.forClass(DebtTransaction.class);
        verify(debtTransactionRepository, times(3)).save(txns.capture());
        List<DebtTxnType> types = txns.getAllValues().stream().map(DebtTransaction::getTxnType).toList();
        assertTrue(types.containsAll(List.of(DebtTxnType.INTEREST_CHARGE, DebtTxnType.LATE_FEE_CHARGE, DebtTxnType.PENALTY_INTEREST_CHARGE)));
        assertEquals(new BigDecimal("100.56"), txns.getAllValues().get(0).getAmount());   // rounded to cents
    }

    @Test
    void addDebt_rejectsAnInterestRateOutsideZeroToHundred() {
        assertThrows(BusinessException.class, () -> service.addDebt(token, dto("100.01")));
        assertThrows(BusinessException.class, () -> service.addDebt(token, dto("-1")));
        verify(debtRepository, never()).save(any());
    }

    @Test
    void addDebt_unknownTypes_areABadRequest() {
        DebtDTO badRepayment = dto("5");
        badRepayment.setRepaymentTypeId(99);
        DebtDTO badDebtType = dto("5");
        badDebtType.setDebtTypeId(99);

        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(BusinessException.class, () -> service.addDebt(token, badRepayment)).getStatus());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(BusinessException.class, () -> service.addDebt(token, badDebtType)).getStatus());
    }

    // ---------- update / delete ----------

    @Test
    void updateDebt_copiesTheFields_reactivatesTheDebt_andEvictsTheCache() {
        UUID id = UUID.randomUUID();
        Debt existing = ownDebt(id);
        DebtDTO dto = dto("9");
        dto.setDebtName("new name");
        dto.setPriority(3);
        dto.setIsInformal(true);
        dto.setPrincipalOutstanding(new BigDecimal("7000"));

        Debt saved = service.updateDebt(token, id, dto);

        assertSame(existing, saved);
        assertEquals("new name", saved.getDebtName());
        assertEquals(3, saved.getPriority());
        assertTrue(saved.getIsInformal());
        assertEquals(new BigDecimal("7000"), saved.getPrincipalOutstanding());
        // current behaviour: an update always re-activates the debt, whatever the request says
        assertTrue(saved.getActive());
        verify(repaymentPlanService).evictUserCache(userId);
    }

    @Test
    void updateDebt_unknownDebt_invalidRateAndUnknownTypes_areRejected() {
        UUID id = UUID.randomUUID();
        when(debtRepository.findByDebtIdAndUserId(id, userId)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(BusinessException.class, () -> service.updateDebt(token, id, dto("5"))).getStatus());

        ownDebt(id);
        assertThrows(BusinessException.class, () -> service.updateDebt(token, id, dto("101")));
        DebtDTO badRepayment = dto("5");
        badRepayment.setRepaymentTypeId(99);
        assertThrows(BusinessException.class, () -> service.updateDebt(token, id, badRepayment));
        DebtDTO badDebtType = dto("5");
        badDebtType.setDebtTypeId(99);
        assertThrows(BusinessException.class, () -> service.updateDebt(token, id, badDebtType));
        verify(debtRepository, never()).save(any());
    }

    @Test
    void deleteDebt_removesItOnlyWhenItBelongsToTheCaller() {
        UUID id = UUID.randomUUID();
        Debt debt = ownDebt(id);

        assertSame(debt, service.deleteDebt(token, id));

        verify(debtRepository).delete(debt);
        verify(repaymentPlanService).evictUserCache(userId);

        UUID other = UUID.randomUUID();
        when(debtRepository.findByDebtIdAndUserId(other, userId)).thenReturn(Optional.empty());
        assertThrows(BusinessException.class, () -> service.deleteDebt(token, other));
    }

    @Test
    void getDebtDetail_ownDebtOnly() {
        UUID id = UUID.randomUUID();
        Debt debt = ownDebt(id);
        assertSame(debt, service.getDebtDetail(token, id));

        UUID other = UUID.randomUUID();
        when(debtRepository.findByDebtIdAndUserId(other, userId)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(BusinessException.class, () -> service.getDebtDetail(token, other)).getStatus());
    }

    // ---------- priorities ----------

    @Test
    void priorities_areSortedAscending_andMissingValuesFallBack() {
        Debt first = new Debt();
        first.setDebtId(UUID.randomUUID());
        first.setDebtName("first");
        first.setPriority(1);
        first.setPrincipalAmount(new BigDecimal("500"));
        first.setPrincipalOutstanding(new BigDecimal("300"));
        Debt unranked = new Debt();
        unranked.setDebtId(UUID.randomUUID());
        unranked.setDebtName("unranked");
        unranked.setPrincipalAmount(new BigDecimal("900"));
        when(debtRepository.findAllByUserId(userId)).thenReturn(List.of(unranked, first));

        List<DebtPriorityResponseDTO> result = service.getDebtPriorities(token);

        assertEquals("unranked", result.get(0).getDebtName());       // null priority counts as 0, so it sorts first
        assertEquals("first", result.get(1).getDebtName());
        assertEquals(new BigDecimal("900"), result.get(0).getPrincipalOutstanding());   // falls back to the principal
        assertEquals(new BigDecimal("300"), result.get(1).getPrincipalOutstanding());
    }

    @Test
    void updatePriorities_savesAllOrNothing() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        Debt debtA = ownDebt(a);
        Debt debtB = ownDebt(b);
        DebtPriorityUpdateRequestDTO ra = new DebtPriorityUpdateRequestDTO();
        ra.setDebtId(a);
        ra.setPriority(2);
        DebtPriorityUpdateRequestDTO rb = new DebtPriorityUpdateRequestDTO();
        rb.setDebtId(b);
        rb.setPriority(1);

        service.updateDebtPriorities(token, List.of(ra, rb));

        assertEquals(2, debtA.getPriority());
        assertEquals(1, debtB.getPriority());
        verify(debtRepository).saveAll(List.of(debtA, debtB));
        verify(repaymentPlanService).evictUserCache(userId);
    }

    @Test
    void updatePriorities_oneUnknownDebt_failsBeforeAnythingIsSaved() {
        UUID mine = UUID.randomUUID();
        ownDebt(mine);
        UUID foreign = UUID.randomUUID();
        when(debtRepository.findByDebtIdAndUserId(foreign, userId)).thenReturn(Optional.empty());
        DebtPriorityUpdateRequestDTO ok = new DebtPriorityUpdateRequestDTO();
        ok.setDebtId(mine);
        ok.setPriority(1);
        DebtPriorityUpdateRequestDTO bad = new DebtPriorityUpdateRequestDTO();
        bad.setDebtId(foreign);
        bad.setPriority(2);

        assertThrows(BusinessException.class, () -> service.updateDebtPriorities(token, List.of(ok, bad)));

        verify(debtRepository, never()).saveAll(any());
    }

    // ---------- summary and history ----------

    @Test
    void debtSummary_includesThePlannedPaymentOfThisMonth() {
        UUID id = UUID.randomUUID();
        Debt debt = ownDebt(id);
        debt.setPrincipalOutstanding(new BigDecimal("1000"));
        when(repaymentPlanService.calculateCurrentMonthAllocation(userId)).thenReturn(Map.of(id, new BigDecimal("250")));

        DebtSummaryDTO summary = service.getDebtSummary(token, id);

        assertEquals(new BigDecimal("1000"), summary.getPrincipalRemaining());
        assertEquals(new BigDecimal("250"), summary.getPlannedPayment());
    }

    @Test
    void debtSummary_withoutAPlanAllocation_plansZero_andUnknownDebtIsNotFound() {
        UUID id = UUID.randomUUID();
        ownDebt(id);
        when(repaymentPlanService.calculateCurrentMonthAllocation(userId)).thenReturn(Map.of());

        assertEquals(BigDecimal.ZERO, service.getDebtSummary(token, id).getPlannedPayment());

        UUID other = UUID.randomUUID();
        when(debtRepository.findByDebtIdAndUserId(other, userId)).thenReturn(Optional.empty());
        assertThrows(BusinessException.class, () -> service.getDebtSummary(token, other));
    }

    @Test
    void history_mapsEveryTransactionTypeToAThaiDescription() {
        UUID id = UUID.randomUUID();
        ownDebt(id);
        DebtTxnType[] types = DebtTxnType.values();
        List<DebtTransaction> txns = new java.util.ArrayList<>();
        for (DebtTxnType type : types) {
            txns.add(DebtTransaction.builder().transactionId(UUID.randomUUID()).txnType(type)
                    .amount(BigDecimal.TEN).txnDate(LocalDate.of(2026, 11, 1)).slipId(7L).build());
        }
        txns.add(DebtTransaction.builder().transactionId(UUID.randomUUID()).txnType(null).amount(BigDecimal.ONE)
                .txnDate(LocalDate.of(2026, 11, 2)).build());
        when(debtTransactionRepository.findByDebtDebtIdOrderByTxnDateDesc(id)).thenReturn(txns);

        List<DebtTransactionResponseDTO> history = service.getDebtHistory(token, id);

        assertEquals(types.length + 1, history.size());
        assertEquals("", history.get(types.length).getDescription());              // no type, no description
        assertEquals("ชำระเงินรวม", history.stream().filter(h -> h.getTxnType() == DebtTxnType.PAYMENT).findFirst().orElseThrow().getDescription());
        assertTrue(history.stream().limit(types.length).allMatch(h -> h.getDescription() != null && !h.getDescription().isBlank()));
        assertEquals(7L, history.get(0).getSlipId());
    }

    @Test
    void history_ofAnotherUsersDebt_isNotFound() {
        UUID other = UUID.randomUUID();
        when(debtRepository.findByDebtIdAndUserId(other, userId)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> service.getDebtHistory(token, other));
        verify(debtTransactionRepository, never()).findByDebtDebtIdOrderByTxnDateDesc(any());
    }

    // ---------- overview graph ----------

    private Category category(String type) {
        Category c = new Category();
        c.setType(type);
        return c;
    }

    private BudgetOverviewDto budget(double amount) {
        BudgetOverviewDto dto = new BudgetOverviewDto();
        dto.setAmount(amount);
        return dto;
    }

    @Test
    void overview_sumsIncomeAndListsTheDebtBalances() {
        Debt card = new Debt();
        card.setDebtName("Card");
        card.setPrincipalAmount(new BigDecimal("5000"));
        when(debtRepository.findAllByUserId(userId)).thenReturn(List.of(card));
        when(categoryRepository.findByUserId(userId)).thenReturn(List.of(category("Expense"), category("Income")));
        when(budgetService.getAmountFromBudget(token)).thenReturn(List.of(budget(100), budget(50)));

        OverviewGraphDTO overview = service.getOverviewGraph(token);

        assertEquals(150.0, overview.getIncome());
        assertEquals(1, overview.getDebtGraphDTO().size());
        assertEquals("Card", overview.getDebtGraphDTO().get(0).getDebtName());
        assertEquals(2, overview.getBudgetDTO().size());
    }

    @Test
    void overview_withoutIncomeOrExpenseCategories_isEmpty() {
        when(debtRepository.findAllByUserId(userId)).thenReturn(List.of());
        when(categoryRepository.findByUserId(userId)).thenReturn(List.of());
        when(budgetService.getAmountFromBudget(token)).thenReturn(List.of(budget(100)));

        OverviewGraphDTO overview = service.getOverviewGraph(token);

        assertEquals(0.0, overview.getIncome());
        assertTrue(overview.getBudgetDTO().isEmpty());
        assertTrue(overview.getDebtGraphDTO().isEmpty());
    }
}
