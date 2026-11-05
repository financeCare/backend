package com.example.capstone.service;

import com.example.capstone.dto.TransactionRequest;
import com.example.capstone.dto.TransactionResponse;
import com.example.capstone.entity.Budget;
import com.example.capstone.entity.Category;
import com.example.capstone.entity.Transaction;
import com.example.capstone.exception.BusinessException;
import com.example.capstone.repository.BudgetRepository;
import com.example.capstone.repository.CategoryRepository;
import com.example.capstone.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Budget bookkeeping around transactions: create, update, delete, extra income and the monthly figures. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TransactionServiceBehaviourTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Bangkok");

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private UserService userService;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private BudgetRepository budgetRepository;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private TransactionService service;

    private UUID userId;
    private final String token = "token";

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        when(userService.extractUserIdFromToken(token)).thenReturn(userId);
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> i.getArgument(0));
    }

    private Budget budget(double amount) {
        Budget b = new Budget();
        b.setBudgetId(UUID.randomUUID());
        b.setUserId(userId);
        b.setAmount(amount);
        b.setLimitBudget(1000.0);
        when(budgetRepository.findByUserIdAndBudgetId(userId, b.getBudgetId())).thenReturn(Optional.of(b));
        return b;
    }

    private Category category(int id, String name, String type, Budget budget) {
        Category c = new Category();
        c.setCategoryId(id);
        c.setCategoryName(name);
        c.setType(type);
        c.setBudgetId(budget.getBudgetId());
        when(categoryRepository.findByCategoryIdAndUserId(id, userId)).thenReturn(Optional.of(c));
        // createTransaction looked categories up by id alone until the ownership fix; stubbing both keeps this test valid on either side
        when(categoryRepository.findById(id)).thenReturn(Optional.of(c));
        return c;
    }

    private Transaction transaction(double amount, Category category) {
        Transaction t = new Transaction();
        t.setTransactionId(UUID.randomUUID());
        t.setUserId(userId);
        t.setAmount(amount);
        t.setCategory(category);
        t.setTransactionDate(LocalDateTime.now(ZONE));
        when(transactionRepository.findByTransactionIdAndUserId(t.getTransactionId(), userId)).thenReturn(Optional.of(t));
        return t;
    }

    private TransactionRequest request(int categoryId, double amount) {
        TransactionRequest r = new TransactionRequest();
        r.setCategoryId(categoryId);
        r.setAmount(amount);
        r.setTransactionDate(LocalDateTime.now(ZONE));
        r.setDescription("lunch");
        return r;
    }

    // ---------- update: the budget must follow the change, not grow with every edit ----------

    @Test
    void update_sameCategory_budgetMovesByTheDifference() {
        Budget food = budget(500);
        Category foodCategory = category(1, "Food", "Expense", food);
        Transaction t = transaction(100, foodCategory);

        service.updateTransaction(token, t.getTransactionId(), request(1, 150));

        assertEquals(550.0, food.getAmount());        // 500 - 100 + 150, not 500 + 150
        assertEquals(150.0, t.getAmount());
        assertEquals("lunch", t.getDescription());
        verify(budgetRepository).save(food);
    }

    @Test
    void update_toAnotherCategory_movesTheAmountBetweenTheTwoBudgets() {
        Budget food = budget(500);
        Budget travel = budget(200);
        Category foodCategory = category(1, "Food", "Expense", food);
        category(2, "Travel", "Expense", travel);
        Transaction t = transaction(100, foodCategory);

        service.updateTransaction(token, t.getTransactionId(), request(2, 150));

        assertEquals(400.0, food.getAmount());        // the old 100 leaves
        assertEquals(350.0, travel.getAmount());      // the new 150 arrives
        assertEquals(2, t.getCategory().getCategoryId());
    }

    @Test
    void update_unknownTransactionCategoryOrBudget_isNotFound() {
        UUID missing = UUID.randomUUID();
        when(transactionRepository.findByTransactionIdAndUserId(missing, userId)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND,
                assertThrows(BusinessException.class, () -> service.updateTransaction(token, missing, request(1, 1))).getStatus());

        Budget food = budget(500);
        Transaction t = transaction(100, category(1, "Food", "Expense", food));
        when(categoryRepository.findByCategoryIdAndUserId(9, userId)).thenReturn(Optional.empty());
        assertThrows(BusinessException.class, () -> service.updateTransaction(token, t.getTransactionId(), request(9, 1)));

        Category orphan = new Category();
        orphan.setCategoryId(3);
        orphan.setBudgetId(UUID.randomUUID());
        when(categoryRepository.findByCategoryIdAndUserId(3, userId)).thenReturn(Optional.of(orphan));
        assertThrows(BusinessException.class, () -> service.updateTransaction(token, t.getTransactionId(), request(3, 1)));
    }

    // ---------- delete ----------

    @Test
    void delete_returnsTheAmountToTheBudget() {
        Budget food = budget(500);
        Transaction t = transaction(120, category(1, "Food", "Expense", food));

        service.deleteTransaction(token, t.getTransactionId());

        assertEquals(380.0, food.getAmount());
        verify(transactionRepository).delete(t);
    }

    @Test
    void delete_ofSomeoneElsesTransaction_changesNothing() {
        UUID other = UUID.randomUUID();
        when(transactionRepository.findByTransactionIdAndUserId(other, userId)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> service.deleteTransaction(token, other));

        verify(transactionRepository, never()).delete(any());
        verify(budgetRepository, never()).save(any());
    }

    // ---------- create: budget and notification ----------

    @Test
    void create_addsToTheBudgetAndNotifiesAboutIt() {
        Budget food = budget(900);
        category(1, "Food", "Expense", food);

        service.createTransaction(token, request(1, 150));

        assertEquals(1050.0, food.getAmount());
        verify(notificationService).createNotificationRuleForBudget(userId, "Food", 1000.0, 1050.0, food.getBudgetId());
    }

    // ---------- reads ----------

    @Test
    void list_mapsTransactionsToResponsesWithTheirCategory() {
        Budget food = budget(0);
        Transaction t = transaction(40, category(1, "Food", "Expense", food));
        t.setDescription("noodles");
        t.setSlipId(9L);
        when(transactionRepository.findByUserId(userId)).thenReturn(List.of(t));

        List<TransactionResponse> responses = service.getTransactionsByUserId(token);

        assertEquals(1, responses.size());
        assertEquals("Food", responses.get(0).getCategoryDTO().getCategoryName());
        assertEquals("Expense", responses.get(0).getCategoryDTO().getType());
        assertEquals(9L, responses.get(0).getSlipId());
    }

    @Test
    void incomeFilter_keepsOnlyTransactionsInIncomeCategories() {
        Budget b = budget(0);
        Category salary = category(1, "Salary", "Income", b);
        Category food = category(2, "Food", "Expense", b);
        Transaction pay = transaction(30000, salary);
        Transaction lunch = transaction(50, food);
        when(categoryRepository.findByUserIdAndType(userId, "Income")).thenReturn(List.of(salary));
        when(transactionRepository.findAllByUserId(userId)).thenReturn(List.of(pay, lunch));

        assertEquals(List.of(pay), service.filterTransactionByIncome(token));
    }

    @Test
    void byCategory_isScopedToTheCaller() {
        Pageable page = Pageable.unpaged();
        when(transactionRepository.findByUserIdAndCategoryCategoryId(userId, 4, page)).thenReturn(new PageImpl<>(List.of()));

        assertTrue(service.getTransactionByCategory(token, 4, page).isEmpty());
        verify(transactionRepository).findByUserIdAndCategoryCategoryId(userId, 4, page);
    }

    @Test
    void thisMonth_isTrueOnlyForTheCurrentMonthAndYear() {
        Transaction now = new Transaction();
        now.setTransactionDate(LocalDateTime.now(ZONE));
        Transaction lastYear = new Transaction();
        lastYear.setTransactionDate(LocalDateTime.now(ZONE).minusYears(1));
        Transaction lastMonth = new Transaction();
        lastMonth.setTransactionDate(LocalDateTime.now(ZONE).minusMonths(1));

        assertTrue(service.checkThisMonth(now));
        assertFalse(service.checkThisMonth(lastYear));    // same month number, other year
        assertFalse(service.checkThisMonth(lastMonth));
    }

    @Test
    void monthlyExpenses_askForTheCurrentMonthRange() {
        when(transactionRepository.sumByCategoryType(eq(userId), eq("Expense"), any(), any())).thenReturn(1234.5);

        assertEquals(1234.5, service.getMonthlyExpenses(token));

        org.mockito.ArgumentCaptor<LocalDateTime> start = org.mockito.ArgumentCaptor.forClass(LocalDateTime.class);
        org.mockito.ArgumentCaptor<LocalDateTime> end = org.mockito.ArgumentCaptor.forClass(LocalDateTime.class);
        verify(transactionRepository).sumByCategoryType(eq(userId), eq("Expense"), start.capture(), end.capture());
        assertEquals(1, start.getValue().getDayOfMonth());
        assertEquals(start.getValue().plusMonths(1), end.getValue());
    }

    // ---------- extra income ----------

    @Test
    void extraIncome_addsToTheExtraIncomeBudgetAndRecordsATransaction() {
        Budget extraBudget = budget(100);
        Category extra = category(5, "Extra Income", "Income", extraBudget);
        Budget otherBudget = budget(0);
        Category food = category(6, "Food", "Expense", otherBudget);
        when(categoryRepository.findByUserId(userId)).thenReturn(List.of(extra, food));
        when(categoryRepository.findByUserIdAndType(userId, "Income")).thenReturn(List.of(extra));

        service.addExtraIncome(token, 250);

        assertEquals(350.0, extraBudget.getAmount());
        assertEquals(0.0, otherBudget.getAmount());
        org.mockito.ArgumentCaptor<Transaction> saved = org.mockito.ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(saved.capture());
        assertEquals(250.0, saved.getValue().getAmount());
        assertEquals("Extra Income", saved.getValue().getDescription());
        assertEquals(extra, saved.getValue().getCategory());
    }

    @Test
    void extraIncome_withAnExtraIncomeCategoryWhoseBudgetIsMissing_isNotFound() {
        Category extra = new Category();
        extra.setCategoryName("Extra Income");
        extra.setBudgetId(UUID.randomUUID());
        when(categoryRepository.findByUserId(userId)).thenReturn(List.of(extra));

        assertThrows(BusinessException.class, () -> service.addExtraIncome(token, 10));
    }
}
