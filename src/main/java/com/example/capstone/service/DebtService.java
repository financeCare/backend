package com.example.capstone.service;

import com.example.capstone.dto.*;
import com.example.capstone.entity.*;
import com.example.capstone.enums.InterestInterval;
import com.example.capstone.enums.PaymentInterval;
import com.example.capstone.enums.DebtTxnType;

import com.example.capstone.exception.BusinessException;
import com.example.capstone.repository.*;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DebtService {
        private final DebtTypeRepository debtTypeRepository;
        private final DebtRepository debtRepository;
        private final RepaymentTypeRepository repaymentTypeRepository;
        private final UserService userService;
        private final BudgetService budgetService;
        private final CategoryRepository categoryRepository;
        private final NotificationService notificationService;
        private final DebtTransactionRepository debtTransactionRepository;
        private final @Lazy RepaymentPlanService repaymentPlanService;

        public List<DebtResponseDTO> getOwnDebt(String token) {
                UUID userId = userService.extractUserIdFromToken(token);
                List<Debt> debts = debtRepository.findAllByUserId(userId);
                Map<UUID, BigDecimal> allocations = repaymentPlanService.calculateCurrentMonthAllocation(userId);

                return debts.stream()
                                .map(debt -> {
                                        DebtSummaryDTO summary = getDebtSummaryInternal(debt);
                                        summary.setPlannedPayment(allocations.getOrDefault(debt.getDebtId(), BigDecimal.ZERO));
                                        return DebtResponseDTO.builder()
                                                        .debt(debt)
                                                        .summary(summary)
                                                        .build();
                                })
                                .toList();
        }

        public Debt getDebtDetail(String token, UUID debtId) {
                UUID userId = userService.extractUserIdFromToken(token);
                return debtRepository.findByDebtIdAndUserId(debtId, userId)
                                .orElseThrow(() -> new BusinessException("Debt not found or not owned by this user",
                                                HttpStatus.NOT_FOUND));
        }

        public OverviewGraphDTO getOverviewGraph(String token) {
                UUID userId = userService.extractUserIdFromToken(token);
                List<Debt> debts = debtRepository.findAllByUserId(userId);
                List<Category> categories = categoryRepository.findByUserId(userId);
                List<BudgetOverviewDto> budgetDTOs = budgetService.getAmountFromBudget(token);
                List<BudgetOverviewDto> expense = budgetDTOs
                                .stream()
                                .filter(b -> categories
                                                .stream()
                                                .anyMatch(c -> c.getType().equals("Expense")))
                                .toList();
                double income = budgetDTOs
                                .stream()
                                .filter(b -> categories
                                                .stream()
                                                .anyMatch(c -> c.getType().equals("Income")))
                                .mapToDouble(BudgetOverviewDto::getAmount)
                                .sum();
                List<DebtGraphDTO> debtGraphDTOs = new ArrayList<>();
                for (Debt debt : debts) {
                        debtGraphDTOs.add(new DebtGraphDTO(debt.getDebtName(), debt.getPrincipalAmount()));
                }
                return new OverviewGraphDTO(income, debtGraphDTOs, expense);
        }

        public Debt addDebt(String token, DebtDTO debtDTO) {
                UUID userId = userService.extractUserIdFromToken(token);
                if (debtDTO.getInterestRate().compareTo(BigDecimal.valueOf(100)) > 0 ||
                                debtDTO.getInterestRate().compareTo(BigDecimal.ZERO) < 0) {
                        throw new BusinessException("invalid interest rate number", HttpStatus.BAD_REQUEST);
                }
                Debt debt = new Debt();
                debt.setUserId(userId);
                debt.setPrincipalAmount(debtDTO.getPrincipalAmount());
                debt.setPrincipalOutstanding(debtDTO.getPrincipalOutstandingV2() != null ? debtDTO.getPrincipalOutstandingV2() : debtDTO.getPrincipalAmount()); // Initialize outstanding balance
                debt.setInterestRate(debtDTO.getInterestRate());
                debt.setRepaymentType(repaymentTypeRepository.findById(debtDTO.getRepaymentTypeId())
                                .orElseThrow(() -> new BusinessException("invalid repayment type id",
                                                HttpStatus.BAD_REQUEST)));
                debt.setStartDate(debtDTO.getStartDate() != null ? debtDTO.getStartDate() : new java.util.Date());
                debt.setEndDate(debtDTO.getEndDate());
                debt.setActive(true);
                debt.setPriority(debtDTO.getPriority());
                debt.setDebtType(debtTypeRepository.findById(debtDTO.getDebtTypeId())
                                .orElseThrow(() -> new BusinessException("invalid debt type id",
                                                HttpStatus.BAD_REQUEST)));
                debt.setDebtName(debt.getDebtName()); // Wait, this should be debtDTO.getDebtName()
                debt.setDebtName(debtDTO.getDebtName());
                debt.setMinPayment(debtDTO.getMinPayment());
                debt.setDueDay(debtDTO.getDueDay());
                debt.setPenaltyAnnualRate(debtDTO.getPenaltyAnnualRate());
                debt.setGracePeriodDays(debtDTO.getGracePeriodDays());
                debt.setPenaltyTriggerDays(debtDTO.getPenaltyTriggerDays());
                debt.setDefaulted(debtDTO.isDefaulted());
                debt.setIsInformal(debtDTO.getIsInformal() != null ? debtDTO.getIsInformal() : false);
                debt.setInterestCalculationType(debtDTO.getInterestCalculationType());
                debt.setInterestInterval(debtDTO.getInterestInterval() != null ? debtDTO.getInterestInterval() : InterestInterval.YEARLY);
                debt.setPaymentInterval(debtDTO.getPaymentInterval() != null ? debtDTO.getPaymentInterval() : PaymentInterval.MONTHLY);
                
                // เก็บยอดเริ่มต้นไว้ใน Debt Entity
                debt.setInitialInterestRemaining(debtDTO.getInitialInterestRemaining() != null ? debtDTO.getInitialInterestRemaining() : BigDecimal.ZERO);
                debt.setInitialLateFeeRemaining(debtDTO.getInitialLateFeeRemaining() != null ? debtDTO.getInitialLateFeeRemaining() : BigDecimal.ZERO);
                debt.setInitialPenaltyRemaining(debtDTO.getInitialPenaltyRemaining() != null ? debtDTO.getInitialPenaltyRemaining() : BigDecimal.ZERO);
                
                debtRepository.save(debt);

                // สร้าง Transaction สำหรับยอดค้างชำระเริ่มต้น
                createInitialBalanceTxns(debt);

                notificationService.createNotificationRuleForDebt(userId, debt.getDebtName(), debt.getMinPayment(),
                                debt.getDebtId());
                return debt;
        }

        private void createInitialBalanceTxns(Debt debt) {
                LocalDate txnDate = debt.getStartDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                
                if (debt.getInitialInterestRemaining().compareTo(BigDecimal.ZERO) > 0) {
                        saveInitialTxn(debt, DebtTxnType.INTEREST_CHARGE, debt.getInitialInterestRemaining(), txnDate);
                }
                if (debt.getInitialLateFeeRemaining().compareTo(BigDecimal.ZERO) > 0) {
                        saveInitialTxn(debt, DebtTxnType.LATE_FEE_CHARGE, debt.getInitialLateFeeRemaining(), txnDate);
                }
                if (debt.getInitialPenaltyRemaining().compareTo(BigDecimal.ZERO) > 0) {
                        saveInitialTxn(debt, DebtTxnType.PENALTY_INTEREST_CHARGE, debt.getInitialPenaltyRemaining(), txnDate);
                }
        }

        private void saveInitialTxn(Debt debt, DebtTxnType type, BigDecimal amount, LocalDate date) {
                DebtTransaction txn = DebtTransaction.builder()
                                .debt(debt)
                                .txnType(type)
                                .amount(amount.setScale(2, RoundingMode.HALF_UP))
                                .txnDate(date)
                                .build();
                debtTransactionRepository.save(txn);
        }

        public Debt updateDebt(String token, UUID debtId, DebtDTO debtDTO) {
                UUID userId = userService.extractUserIdFromToken(token);
                Debt existingDebt = debtRepository.findByDebtIdAndUserId(debtId, userId)
                                .orElseThrow(() -> new BusinessException("Debt not found or not owned by this user",
                                                HttpStatus.NOT_FOUND));

                if (debtDTO.getInterestRate().compareTo(BigDecimal.valueOf(100)) > 0 ||
                                debtDTO.getInterestRate().compareTo(BigDecimal.ZERO) < 0) {
                        throw new BusinessException("invalid interest rate number", HttpStatus.BAD_REQUEST);
                }

                RepaymentType repaymentType = repaymentTypeRepository.findById(debtDTO.getRepaymentTypeId())
                                .orElseThrow(() -> new BusinessException("invalid repaymentType id",
                                                HttpStatus.BAD_REQUEST));
                DebtType debtType = debtTypeRepository.findById(debtDTO.getDebtTypeId())
                                .orElseThrow(() -> new BusinessException("invalid debtType id",
                                                HttpStatus.BAD_REQUEST));

                existingDebt.setPrincipalAmount(debtDTO.getPrincipalAmount());
                // Update outstanding balance only if provided in DTO
                if (debtDTO.getPrincipalOutstandingV2() != null) {
                        existingDebt.setPrincipalOutstanding(debtDTO.getPrincipalOutstandingV2());
                }
                existingDebt.setInterestRate(debtDTO.getInterestRate());
                existingDebt.setRepaymentType(repaymentType);
                existingDebt.setStartDate(debtDTO.getStartDate());
                existingDebt.setEndDate(debtDTO.getEndDate());
                existingDebt.setActive(debtDTO.isActive());
                existingDebt.setPriority(debtDTO.getPriority());
                existingDebt.setDebtType(debtType);
                existingDebt.setDebtName(debtDTO.getDebtName());
                existingDebt.setMinPayment(debtDTO.getMinPayment());
                existingDebt.setDueDay(debtDTO.getDueDay());
                existingDebt.setPenaltyAnnualRate(debtDTO.getPenaltyAnnualRate());
                existingDebt.setGracePeriodDays(debtDTO.getGracePeriodDays());
                existingDebt.setPenaltyTriggerDays(debtDTO.getPenaltyTriggerDays());
                existingDebt.setDefaulted(debtDTO.isDefaulted());
                existingDebt.setIsInformal(debtDTO.getIsInformal() != null ? debtDTO.getIsInformal() : false);
                existingDebt.setInterestCalculationType(debtDTO.getInterestCalculationType());
                if (debtDTO.getInterestInterval() != null) {
                        existingDebt.setInterestInterval(debtDTO.getInterestInterval());
                }
                if (debtDTO.getPaymentInterval() != null) {
                        existingDebt.setPaymentInterval(debtDTO.getPaymentInterval());
                }
                
                // อัปเดตยอดเริ่มต้น (หมายเหตุ: การแก้ตรงนี้จะแก้ไขเฉพาะในตัว Debt แต่ไม่ไปแก้ Transaction ที่ถูกสร้างไปแล้ว)
                if (debtDTO.getInitialInterestRemaining() != null) existingDebt.setInitialInterestRemaining(debtDTO.getInitialInterestRemaining());
                if (debtDTO.getInitialLateFeeRemaining() != null) existingDebt.setInitialLateFeeRemaining(debtDTO.getInitialLateFeeRemaining());
                if (debtDTO.getInitialPenaltyRemaining() != null) existingDebt.setInitialPenaltyRemaining(debtDTO.getInitialPenaltyRemaining());

                existingDebt.setActive(true);
                return debtRepository.save(existingDebt);
        }

        public Debt deleteDebt(String token, UUID debtId) {
                UUID userId = userService.extractUserIdFromToken(token);
                Debt debt = debtRepository.findByDebtIdAndUserId(debtId, userId)
                                .orElseThrow(() -> new BusinessException("Debt not found or not owned by this user",
                                                HttpStatus.NOT_FOUND));
                debtRepository.delete(debt);
                return debt;
        }
        public List<DebtPriorityResponseDTO> getDebtPriorities(String token) {
                UUID userId = userService.extractUserIdFromToken(token);
                List<Debt> debts = debtRepository.findAllByUserId(userId);
                
                return debts.stream()
                                .map(debt -> new DebtPriorityResponseDTO(
                                                debt.getDebtId(),
                                                debt.getDebtName(),
                                                debt.getPriority() != null ? debt.getPriority() : 0,
                                                debt.getPrincipalAmount(),
                                                debt.getPrincipalOutstanding() != null ? debt.getPrincipalOutstanding() : debt.getPrincipalAmount()
                                ))
                                .sorted((d1, d2) -> Integer.compare(d1.getPriority(), d2.getPriority()))
                                .toList();
        }

        public void updateDebtPriorities(String token, List<DebtPriorityUpdateRequestDTO> requests) {
                UUID userId = userService.extractUserIdFromToken(token);
                List<Debt> debtsToUpdate = new ArrayList<>();
                
                for (DebtPriorityUpdateRequestDTO request : requests) {
                        Debt debt = debtRepository.findByDebtIdAndUserId(request.getDebtId(), userId)
                                        .orElseThrow(() -> new BusinessException("Debt not found or not owned by this user", HttpStatus.NOT_FOUND));
                        
                        debt.setPriority(request.getPriority());
                        debtsToUpdate.add(debt);
                }
                debtRepository.saveAll(debtsToUpdate);
        }

        public DebtSummaryDTO getDebtSummary(String token, UUID debtId) {
                UUID userId = userService.extractUserIdFromToken(token);
                Debt debt = debtRepository.findByDebtIdAndUserId(debtId, userId)
                                .orElseThrow(() -> new BusinessException("Debt not found or not owned by this user",
                                                HttpStatus.NOT_FOUND));

                DebtSummaryDTO summary = getDebtSummaryInternal(debt);
                Map<UUID, BigDecimal> allocations = repaymentPlanService.calculateCurrentMonthAllocation(userId);
                summary.setPlannedPayment(allocations.getOrDefault(debtId, BigDecimal.ZERO));
                return summary;
        }

        public DebtSummaryDTO getDebtSummaryInternal(Debt debt) {
                UUID debtId = debt.getDebtId();
                BigDecimal principal = debt.getPrincipalOutstanding() != null ? 
                                debt.getPrincipalOutstanding() : debt.getPrincipalAmount();
                if (principal == null) principal = BigDecimal.ZERO;
                
                // Total outstanding across all time
                BigDecimal interest = debtTransactionRepository.sumOutstandingByType(debtId, DebtTxnType.INTEREST_CHARGE);
                if (interest == null) interest = BigDecimal.ZERO;
                BigDecimal lateFee = debtTransactionRepository.sumOutstandingByType(debtId, DebtTxnType.LATE_FEE_CHARGE);
                if (lateFee == null) lateFee = BigDecimal.ZERO;
                BigDecimal penalty = debtTransactionRepository.sumOutstandingByType(debtId, DebtTxnType.PENALTY_INTEREST_CHARGE);
                if (penalty == null) penalty = BigDecimal.ZERO;
                
                // Current month outstanding
                java.time.LocalDate now = java.time.LocalDate.now();
                int year = now.getYear();
                int month = now.getMonthValue();
                
                BigDecimal interestMonth = debtTransactionRepository.sumMonthDebt(debtId, DebtTxnType.INTEREST_CHARGE, year, month);
                if (interestMonth == null) interestMonth = BigDecimal.ZERO;
                BigDecimal lateFeeMonth = debtTransactionRepository.sumMonthDebt(debtId, DebtTxnType.LATE_FEE_CHARGE, year, month);
                if (lateFeeMonth == null) lateFeeMonth = BigDecimal.ZERO;
                BigDecimal penaltyMonth = debtTransactionRepository.sumMonthDebt(debtId, DebtTxnType.PENALTY_INTEREST_CHARGE, year, month);
                if (penaltyMonth == null) penaltyMonth = BigDecimal.ZERO;

                BigDecimal paidThisMonth = debtTransactionRepository.sumMonthDebt(debtId, DebtTxnType.PAYMENT, year, month);
                if (paidThisMonth == null) paidThisMonth = BigDecimal.ZERO;

                BigDecimal total = principal.add(interest).add(lateFee).add(penalty);

                return DebtSummaryDTO.builder()
                                .principalRemaining(principal)
                                .interestRemaining(interest)
                                .lateFeeRemaining(lateFee)
                                .penaltyInterestRemaining(penalty)
                                .interestRemainingMonth(interestMonth)
                                .lateFeeRemainingMonth(lateFeeMonth)
                                .penaltyInterestRemainingMonth(penaltyMonth)
                                .totalRemaining(total)
                                .paidThisMonth(paidThisMonth)
                                .build();
        }

        public List<DebtTransactionResponseDTO> getDebtHistory(String token, UUID debtId) {
                UUID userId = userService.extractUserIdFromToken(token);
                debtRepository.findByDebtIdAndUserId(debtId, userId)
                                .orElseThrow(() -> new BusinessException("Debt not found or not owned by this user",
                                                HttpStatus.NOT_FOUND));

                List<DebtTransaction> txns = debtTransactionRepository.findByDebtDebtIdOrderByTxnDateDesc(debtId);

                return txns.stream()
                                .map(this::mapToTransactionResponse)
                                .toList();
        }

        private DebtTransactionResponseDTO mapToTransactionResponse(DebtTransaction txn) {
                return DebtTransactionResponseDTO.builder()
                                .transactionId(txn.getTransactionId())
                                .txnType(txn.getTxnType())
                                .amount(txn.getAmount())
                                .txnDate(txn.getTxnDate())
                                .description(getThaiDescription(txn.getTxnType()))
                                .slipId(txn.getSlipId())
                                .build();
        }

        private String getThaiDescription(DebtTxnType type) {
                if (type == null)
                        return "";
                return switch (type) {
                        case PAYMENT -> "ชำระเงินรวม";
                        case INTEREST_CHARGE -> "ดอกเบี้ยรายเดือน";
                        case LATE_FEE_CHARGE -> "ค่าธรรมเนียมชำระล่าช้า";
                        case PENALTY_INTEREST_CHARGE -> "ดอกเบี้ยผิดนัดชำระ";
                        case OVERPAYMENT_FEE_CHARGE -> "ค่าธรรมเนียมชำระเกิน";
                        case INTEREST_PAYMENT -> "ชำระดอกเบี้ย";
                        case LATE_FEE_PAYMENT -> "ชำระค่าธรรมเนียมล่าช้า";
                        case PENALTY_INTEREST_PAYMENT -> "ชำระดอกเบี้ยผิดนัด";
                        case OVERPAYMENT_FEE_PAYMENT -> "ชำระค่าธรรมเนียมเงินเกิน";
                        case PRINCIPAL_PAYMENT -> "ชำระเงินต้น";
                        case OVERPAYMENT -> "เงินจ่ายเกินค้างไว้ในระบบ";
                        default -> type.name();
                };
        }

}