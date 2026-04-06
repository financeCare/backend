package com.example.capstone.repository;

import com.example.capstone.entity.Category;
import com.example.capstone.entity.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
public class TransactionRepositoryTest {

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private TestEntityManager entityManager;

    private UUID userId;
    private Category incomeCategory;
    private Category expenseCategory;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();

        incomeCategory = new Category();
        incomeCategory.setCategoryName("Salary");
        incomeCategory.setType("INCOME");
        entityManager.persist(incomeCategory);

        expenseCategory = new Category();
        expenseCategory.setCategoryName("Food");
        expenseCategory.setType("EXPENSE");
        entityManager.persist(expenseCategory);

        Transaction t1 = new Transaction();
        t1.setUserId(userId);
        t1.setAmount(1000.00);
        t1.setCategory(incomeCategory);
        t1.setTransactionDate(LocalDateTime.now().minusDays(1));
        entityManager.persist(t1);

        Transaction t2 = new Transaction();
        t2.setUserId(userId);
        t2.setAmount(500.00);
        t2.setCategory(expenseCategory);
        t2.setTransactionDate(LocalDateTime.now().minusDays(1));
        entityManager.persist(t2);

        entityManager.flush();
    }

    @Test
    void testFindByUserId() {
        List<Transaction> transactions = transactionRepository.findByUserId(userId);
        assertEquals(2, transactions.size());
    }

    @Test
    void testSumByCategoryType() {
        LocalDateTime start = LocalDateTime.now().minusDays(2);
        LocalDateTime end = LocalDateTime.now().plusDays(1);

        Double totalExpense = transactionRepository.sumByCategoryType(userId, "EXPENSE", start, end);
        assertEquals(500.0, totalExpense);
    }

    @Test
    void testSumIncomeByCategoryName() {
        LocalDateTime start = LocalDateTime.now().minusDays(2);
        LocalDateTime end = LocalDateTime.now().plusDays(1);

        Double totalSalary = transactionRepository.sumIncomeByCategoryName(userId, "INCOME", "Salary", start, end);
        assertEquals(1000.0, totalSalary);
    }
}
