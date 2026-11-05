package com.example.capstone.integration;

import com.example.capstone.entity.Debt;
import com.example.capstone.entity.DebtTransaction;
import com.example.capstone.entity.DebtType;
import com.example.capstone.entity.RepaymentType;
import com.example.capstone.enums.DebtTxnType;
import com.example.capstone.repository.DebtRepository;
import com.example.capstone.repository.DebtTransactionRepository;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The repository queries against a real PostgreSQL (the other repository tests run on H2). Skipped when Docker is
 * not available. Covers the batched queries that replaced the per-debt N+1 queries.
 */
@DataJpaTest(properties = {
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class PostgresRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private TestEntityManager entityManager;
    @Autowired
    private DebtTransactionRepository debtTransactionRepository;
    @Autowired
    private DebtRepository debtRepository;
    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private Debt persistDebt(UUID userId, String name, DebtType debtType, RepaymentType repaymentType) {
        Debt debt = new Debt();
        debt.setUserId(userId);
        debt.setDebtName(name);
        debt.setDebtType(debtType);
        debt.setRepaymentType(repaymentType);
        return entityManager.persist(debt);
    }

    private void txn(Debt debt, DebtTxnType type, String amount, LocalDate date) {
        entityManager.persist(DebtTransaction.builder()
                .debt(debt).txnType(type).amount(new BigDecimal(amount)).txnDate(date).build());
    }

    @Test
    void batchedSumsMatchTheOriginalPerDebtQueries() {
        UUID userId = UUID.randomUUID();
        Debt a = persistDebt(userId, "A", null, null);
        Debt b = persistDebt(userId, "B", null, null);
        Debt untouched = persistDebt(userId, "no transactions", null, null);
        LocalDate thisMonth = LocalDate.of(2026, 11, 10);
        LocalDate lastMonth = LocalDate.of(2026, 10, 10);

        txn(a, DebtTxnType.INTEREST_CHARGE, "100.00", thisMonth);
        txn(a, DebtTxnType.INTEREST_CHARGE, "50.00", lastMonth);
        txn(a, DebtTxnType.INTEREST_CHARGE, "-30.00", thisMonth);        // negative amounts are not outstanding
        txn(a, DebtTxnType.LATE_FEE_CHARGE, "20.00", thisMonth);
        txn(a, DebtTxnType.PAYMENT, "400.00", thisMonth);
        txn(b, DebtTxnType.INTEREST_CHARGE, "7.25", thisMonth);
        txn(b, DebtTxnType.PENALTY_INTEREST_CHARGE, "9.00", lastMonth);
        entityManager.flush();
        entityManager.clear();

        List<DebtTxnType> outstandingTypes = List.of(
                DebtTxnType.INTEREST_CHARGE, DebtTxnType.LATE_FEE_CHARGE, DebtTxnType.PENALTY_INTEREST_CHARGE);
        List<DebtTxnType> monthTypes = List.of(
                DebtTxnType.INTEREST_CHARGE, DebtTxnType.LATE_FEE_CHARGE, DebtTxnType.PENALTY_INTEREST_CHARGE, DebtTxnType.PAYMENT);
        List<UUID> ids = List.of(a.getDebtId(), b.getDebtId(), untouched.getDebtId());

        Map<UUID, Map<DebtTxnType, BigDecimal>> outstanding = debtTransactionRepository.outstandingByDebt(ids, outstandingTypes);
        Map<UUID, Map<DebtTxnType, BigDecimal>> month = debtTransactionRepository.monthByDebt(ids, monthTypes, 2026, 11);

        for (UUID id : ids) {
            for (DebtTxnType type : outstandingTypes) {
                BigDecimal expected = debtTransactionRepository.sumOutstandingByType(id, type);
                BigDecimal actual = outstanding.getOrDefault(id, Map.of()).getOrDefault(type, BigDecimal.ZERO);
                assertEquals(0, expected.compareTo(actual), "outstanding " + type + " for " + id);
            }
            for (DebtTxnType type : monthTypes) {
                BigDecimal expected = debtTransactionRepository.sumMonthDebt(id, type, 2026, 11);
                BigDecimal actual = month.getOrDefault(id, Map.of()).getOrDefault(type, BigDecimal.ZERO);
                assertEquals(0, expected.compareTo(actual), "month " + type + " for " + id);
            }
        }
        assertEquals(0, new BigDecimal("150.00").compareTo(outstanding.get(a.getDebtId()).get(DebtTxnType.INTEREST_CHARGE)));
        assertTrue(!outstanding.containsKey(untouched.getDebtId()), "a debt without transactions has no rows");
    }

    @Test
    void emptyDebtIdListReturnsEmptyWithoutQuerying() {
        assertTrue(debtTransactionRepository.outstandingByDebt(List.of(), List.of(DebtTxnType.INTEREST_CHARGE)).isEmpty());
        assertTrue(debtTransactionRepository.monthByDebt(List.of(), List.of(DebtTxnType.PAYMENT), 2026, 11).isEmpty());
    }

    @Test
    void findAllByUserIdLoadsTheDebtTypesInASingleQuery() {
        UUID userId = UUID.randomUUID();
        DebtType debtType = entityManager.persist(new DebtType(null, "Credit Card", "Card debt"));
        RepaymentType repaymentType = entityManager.persist(new RepaymentType(null, "EMI", "Monthly instalments"));
        for (int i = 0; i < 5; i++) {
            persistDebt(userId, "debt " + i, debtType, repaymentType);
        }
        entityManager.flush();
        entityManager.clear();

        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        sessionFactory.getStatistics().clear();

        List<Debt> debts = debtRepository.findAllByUserId(userId);
        debts.forEach(d -> {
            assertEquals("Credit Card", d.getDebtType().getDebtTypeName());
            assertEquals("EMI", d.getRepaymentType().getRepaymentTypeName());
        });

        assertEquals(5, debts.size());
        assertEquals(1, sessionFactory.getStatistics().getPrepareStatementCount(),
                "debts and their types must come from one JOIN FETCH query, not 1 + N");
    }
}
