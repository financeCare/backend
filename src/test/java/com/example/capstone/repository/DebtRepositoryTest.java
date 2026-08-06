package com.example.capstone.repository;

import com.example.capstone.entity.Debt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
public class DebtRepositoryTest {

    @Autowired
    private DebtRepository debtRepository;

    @Autowired
    private TestEntityManager entityManager;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        
        Debt debt1 = new Debt();
        debt1.setUserId(userId);
        debt1.setDebtName("Credit Card A");
        debt1.setPrincipalAmount(new BigDecimal("50000.00"));
        debt1.setActive(true);
        debt1.setDueDay(5);
        entityManager.persist(debt1);

        Debt debt2 = new Debt();
        debt2.setUserId(userId);
        debt2.setDebtName("Personal Loan B");
        debt2.setPrincipalAmount(new BigDecimal("100000.00"));
        debt2.setActive(false);
        debt2.setDueDay(15);
        entityManager.persist(debt2);

        entityManager.flush();
    }

    @Test
    void testFindAllByUserId() {
        List<Debt> debts = debtRepository.findAllByUserId(userId);
        assertEquals(2, debts.size());
    }

    @Test
    void testFindByActiveAndUserId() {
        List<Debt> activeDebts = debtRepository.findByActiveAndUserId(true, userId);
        assertEquals(1, activeDebts.size());
        assertEquals("Credit Card A", activeDebts.get(0).getDebtName());
    }

    @Test
    void testFindByActiveTrueAndDueDay() {
        List<Debt> debtsDueOn5 = debtRepository.findByActiveTrueAndDueDay(5);
        assertEquals(1, debtsDueOn5.size());
        assertEquals("Credit Card A", debtsDueOn5.get(0).getDebtName());
    }

    @Test
    void testFindByActiveTrue() {
        List<Debt> activeDebts = debtRepository.findByActiveTrue();
        assertFalse(activeDebts.isEmpty());
        assertTrue(activeDebts.stream().allMatch(Debt::getActive));
    }
}
