package com.example.capstone.service;

import com.example.capstone.dto.BudgetOverviewDto;
import com.example.capstone.entity.Budget;
import com.example.capstone.entity.Category;
import com.example.capstone.entity.User;
import com.example.capstone.exception.BusinessException;
import com.example.capstone.repository.BudgetRepository;
import com.example.capstone.repository.CategoryRepository;
import com.example.capstone.repository.UserRepository;
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

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Budget limits, the salary budget, the monthly overview and the monthly rollover of budgets. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BudgetServiceBehaviourTest {

    @Mock
    private BudgetRepository budgetRepository;
    @Mock
    private UserService userService;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private BudgetService service;

    private UUID userId;
    private final String token = "token";

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        when(userService.extractUserIdFromToken(token)).thenReturn(userId);
        when(budgetRepository.save(any(Budget.class))).thenAnswer(i -> {
            Budget b = i.getArgument(0);
            if (b.getBudgetId() == null) {
                b.setBudgetId(UUID.randomUUID());
            }
            return b;
        });
    }

    private Budget budget(double amount, double limit, int month, int year) {
        Budget b = new Budget();
        b.setBudgetId(UUID.randomUUID());
        b.setUserId(userId);
        b.setAmount(amount);
        b.setLimitBudget(limit);
        b.setMonth(month);
        b.setYear(year);
        when(budgetRepository.findById(b.getBudgetId())).thenReturn(Optional.of(b));
        return b;
    }

    private Category category(int id, String name, String type, Budget budget) {
        Category c = new Category();
        c.setCategoryId(id);
        c.setCategoryName(name);
        c.setType(type);
        c.setUserId(userId);
        c.setBudgetId(budget == null ? null : budget.getBudgetId());
        return c;
    }

    // ---------- limits ----------

    @Test
    void updateLimit_ownBudget_isChangedAndSaved() {
        Budget b = budget(100, 500, 11, 2026);
        when(budgetRepository.findByUserIdAndBudgetId(userId, b.getBudgetId())).thenReturn(Optional.of(b));

        Budget updated = service.updateLimitAmountBudget(token, b.getBudgetId(), 800);

        assertEquals(800.0, updated.getLimitBudget());
        verify(budgetRepository).save(b);
    }

    @Test
    void updateLimit_unknownBudget_isNotFound() {
        UUID other = UUID.randomUUID();
        when(budgetRepository.findByUserIdAndBudgetId(userId, other)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class, () -> service.updateLimitAmountBudget(token, other, 10));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        verify(budgetRepository, never()).save(any());
    }

    @Test
    void createAndDeleteAndList_delegateToTheRepository() {
        Budget b = new Budget();
        service.createBudget(b);
        verify(budgetRepository).save(b);

        UUID id = UUID.randomUUID();
        service.deleteBudget(id);
        verify(budgetRepository).deleteById(id);

        when(budgetRepository.findByUserId(userId)).thenReturn(List.of(b));
        assertEquals(List.of(b), service.getAllBudgets(token));
    }

    // ---------- overview ----------

    @Test
    void transactionOverview_listsEveryBudgetExceptTheSalaryOne() {
        Budget food = budget(120, 1000, 11, 2026);
        Budget salary = budget(30000, 0, 11, 2026);
        when(budgetRepository.findByUserId(userId)).thenReturn(List.of(food, salary));
        when(categoryRepository.findByUserIdAndBudgetIdAndCategoryNameNot(userId, food.getBudgetId(), "Salary"))
                .thenReturn(category(1, "Food", "Expense", food));
        when(categoryRepository.findByUserIdAndBudgetIdAndCategoryNameNot(userId, salary.getBudgetId(), "Salary"))
                .thenReturn(null);

        List<BudgetOverviewDto> overview = service.getTransactionOverview(token);

        assertEquals(1, overview.size());
        assertEquals("Food", overview.get(0).getCategoryName());
        assertEquals(120.0, overview.get(0).getAmount());
        assertEquals(1000.0, overview.get(0).getLimit());
        assertEquals(food.getBudgetId().toString(), overview.get(0).getBudgetId());
    }

    @Test
    void incomeAmount_sumsTheBudgetsThatAreNotExpenseBudgets() {
        Budget salary = budget(30000, 0, 11, 2026);
        Budget bonus = budget(2000, 0, 11, 2026);
        Budget food = budget(500, 1000, 11, 2026);
        when(budgetRepository.findByUserId(userId)).thenReturn(List.of(salary, bonus, food));
        when(categoryRepository.findByUserIdAndBudgetIdAndTypeNot(userId, salary.getBudgetId(), "Expense"))
                .thenReturn(category(1, "Salary", "Income", salary));
        when(categoryRepository.findByUserIdAndBudgetIdAndTypeNot(userId, bonus.getBudgetId(), "Expense"))
                .thenReturn(category(2, "Bonus", "Income", bonus));
        when(categoryRepository.findByUserIdAndBudgetIdAndTypeNot(userId, food.getBudgetId(), "Expense")).thenReturn(null);

        assertEquals(32000.0, service.getIncomeAmount(token));
    }

    // ---------- salary ----------

    @Test
    void salary_isReadFromAndWrittenToTheSalaryBudget() {
        Budget salaryBudget = budget(30000, 0, 11, 2026);
        Budget other = budget(5, 5, 11, 2026);
        Category salary = category(1, "Salary", "Income", salaryBudget);
        when(categoryRepository.findByUserIdAndCategoryName(userId, "Salary")).thenReturn(salary);
        when(budgetRepository.findByUserId(userId)).thenReturn(List.of(other, salaryBudget));

        assertEquals(30000.0, service.getSalary(token));

        service.setSalary(token, 35000);

        assertEquals(35000.0, salaryBudget.getAmount());
        assertEquals(5.0, other.getAmount());
        verify(budgetRepository, times(1)).save(salaryBudget);
    }

    @Test
    void salary_withoutAMatchingBudget_isZeroAndNothingIsSaved() {
        Budget salaryBudget = budget(1, 1, 11, 2026);
        Category salary = category(1, "Salary", "Income", salaryBudget);
        when(categoryRepository.findByUserIdAndCategoryName(userId, "Salary")).thenReturn(salary);
        when(budgetRepository.findByUserId(userId)).thenReturn(List.of());

        assertEquals(0.0, service.getSalary(token));
        service.setSalary(token, 10);
        verify(budgetRepository, never()).save(any());
    }

    // ---------- monthly rollover ----------

    @Test
    void ensureBudgetMonth_startsAFreshBudgetForANewMonth_keepingTheLimit() {
        Budget old = budget(700, 1500, 10, 2026);
        Category food = category(1, "Food", "Expense", old);
        when(categoryRepository.findByUserId(userId)).thenReturn(List.of(food));

        service.ensureBudgetMonth(userId, 11, 2026);

        ArgumentCaptor<Budget> saved = ArgumentCaptor.forClass(Budget.class);
        verify(budgetRepository).save(saved.capture());
        Budget fresh = saved.getValue();
        assertEquals(0.0, fresh.getAmount());                // spending starts again
        assertEquals(1500.0, fresh.getLimitBudget());        // the limit carries over
        assertEquals(11, fresh.getMonth());
        assertEquals(2026, fresh.getYear());
        assertNotEquals(old.getBudgetId(), food.getBudgetId());
        assertEquals(fresh.getBudgetId(), food.getBudgetId());   // the category points at the new budget
        verify(categoryRepository).save(food);
    }

    @Test
    void ensureBudgetMonth_leavesCurrentBudgetsAndCategoriesWithoutOneAlone() {
        Budget current = budget(100, 1000, 11, 2026);
        Category up = category(1, "Food", "Expense", current);
        Category noBudget = category(2, "Misc", "Expense", null);
        Category danglingBudget = category(3, "Gone", "Expense", null);
        danglingBudget.setBudgetId(UUID.randomUUID());       // points at a budget that no longer exists
        when(categoryRepository.findByUserId(userId)).thenReturn(List.of(up, noBudget, danglingBudget));
        when(budgetRepository.findById(danglingBudget.getBudgetId())).thenReturn(Optional.empty());

        service.ensureBudgetMonth(userId, 11, 2026);

        verify(budgetRepository, never()).save(any());
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void ensureBudgetMonth_aNewYearAlsoStartsANewBudget() {
        Budget december = budget(300, 900, 12, 2025);
        Category food = category(1, "Food", "Expense", december);
        when(categoryRepository.findByUserId(userId)).thenReturn(List.of(food));

        service.ensureBudgetMonth(userId, 12, 2026);      // same month number, a year later

        verify(budgetRepository).save(any(Budget.class));
    }

    @Test
    void monthlyRollover_runsForEveryUserWithTheCurrentMonth() {
        UUID second = UUID.randomUUID();
        User u1 = new User();
        u1.setUserId(userId);
        User u2 = new User();
        u2.setUserId(second);
        when(userRepository.findAll()).thenReturn(List.of(u1, u2));
        LocalDate now = LocalDate.now(ZoneId.of("Asia/Bangkok"));
        Budget old1 = budget(10, 100, now.minusMonths(1).getMonthValue(), now.minusMonths(1).getYear());
        when(categoryRepository.findByUserId(userId)).thenReturn(List.of(category(1, "Food", "Expense", old1)));
        when(categoryRepository.findByUserId(second)).thenReturn(List.of());

        service.monthlyRollover();

        ArgumentCaptor<Budget> saved = ArgumentCaptor.forClass(Budget.class);
        verify(budgetRepository, times(1)).save(saved.capture());
        assertEquals(now.getMonthValue(), saved.getValue().getMonth());
        assertEquals(now.getYear(), saved.getValue().getYear());
    }
}
