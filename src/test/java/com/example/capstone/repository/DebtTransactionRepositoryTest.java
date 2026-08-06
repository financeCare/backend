package com.example.capstone.repository;

import com.example.capstone.entity.Debt;
import com.example.capstone.entity.DebtTransaction;
import com.example.capstone.enums.DebtTxnType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
public class DebtTransactionRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private DebtTransactionRepository debtTransactionRepository;

//     @Test
//     void testExistsByDebtAndTxnTypeAndYearAndMonth_Success() {
//         UUID userId = UUID.randomUUID();
//         Debt debt = new Debt();
//         debt.setUserId(userId);
//         debt.setDebtName("Test Debt");
//         entityManager.persist(debt);

//         DebtTransaction txn = DebtTransaction.builder()
//                 .debt(debt)
//                 .txnType(DebtTxnType.INTEREST_CHARGE) // หรือเลือกที่เป็น enum ที่มีอยู่
//                 .amount(new BigDecimal("1000.00"))
//                 .txnDate(LocalDate.of(2026, 3, 27))
//                 .build();
//         entityManager.persist(txn);
//         entityManager.flush();

//         boolean exists = debtTransactionRepository.existsByDebtAndTxnTypeAndYearAndMonth(
//                 debt, DebtTxnType.INTEREST_CHARGE, 2026, 3);

//         assertTrue(exists);
//     }

    @Test
    void testSumPaymentsByUserIdAndMonth() {
        UUID userId = UUID.randomUUID();
        Debt debt = new Debt();
        debt.setUserId(userId);
        debt.setDebtName("Test Debt 2");
        entityManager.persist(debt);

        DebtTransaction txn1 = DebtTransaction.builder()
                .debt(debt)
                .txnType(DebtTxnType.PAYMENT)
                .amount(new BigDecimal("500.00"))
                .txnDate(LocalDate.of(2026, 3, 10))
                .build();
        
        DebtTransaction txn2 = DebtTransaction.builder()
                .debt(debt)
                .txnType(DebtTxnType.PAYMENT)
                .amount(new BigDecimal("300.00"))
                .txnDate(LocalDate.of(2026, 3, 15))
                .build();

        // ของเดือนอื่น
        DebtTransaction txn3 = DebtTransaction.builder()
                .debt(debt)
                .txnType(DebtTxnType.PAYMENT)
                .amount(new BigDecimal("200.00"))
                .txnDate(LocalDate.of(2026, 2, 28))
                .build();

        entityManager.persist(txn1);
        entityManager.persist(txn2);
        entityManager.persist(txn3);
        entityManager.flush();

        BigDecimal sum = debtTransactionRepository.sumPaymentsByUserIdAndMonth(userId, 2026, 3);

        assertEquals(0, new BigDecimal("800.00").compareTo(sum));
    }
}
