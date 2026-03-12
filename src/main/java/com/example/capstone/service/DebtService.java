package com.example.capstone.service;

import com.example.capstone.dto.*;
import com.example.capstone.entity.*;

import com.example.capstone.exception.BusinessException;
import com.example.capstone.repository.*;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

import java.util.ArrayList;

import java.util.List;
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

        public List<Debt> getOwnDebt(String token) {
                UUID userId = userService.extractUserIdFromToken(token);
                return debtRepository.findAllByUserId(userId);
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
                debt.setPrincipalOutstanding(debtDTO.getPrincipalAmount()); // Initialize outstanding balance
                debt.setInterestRate(debtDTO.getInterestRate());
                debt.setRepaymentType(repaymentTypeRepository.findById(debtDTO.getRepaymentTypeId())
                                .orElseThrow(() -> new BusinessException("invalid repayment type id",
                                                HttpStatus.BAD_REQUEST)));
                debt.setStartDate(debtDTO.getStartDate());
                debt.setEndDate(debtDTO.getEndDate());
                debt.setActive(true);
                debt.setPriority(debtDTO.getPriority());
                debt.setDebtType(debtTypeRepository.findById(debtDTO.getDebtTypeId())
                                .orElseThrow(() -> new BusinessException("invalid debt type id",
                                                HttpStatus.BAD_REQUEST)));
                debt.setDebtName(debtDTO.getDebtName());
                debt.setMinPayment(debtDTO.getMinPayment());
                debt.setDueDay(debtDTO.getDueDay());
                debt.setPenaltyAnnualRate(debtDTO.getPenaltyAnnualRate());
                debt.setGracePeriodDays(debtDTO.getGracePeriodDays());
                debt.setPenaltyTriggerDays(debtDTO.getPenaltyTriggerDays());
                debt.setDefaulted(debtDTO.isDefaulted());
                debt.setIsInformal(debtDTO.getIsInformal() != null ? debtDTO.getIsInformal() : false);
                debt.setInterestCalculationType(debtDTO.getInterestCalculationType());
                debtRepository.save(debt);
                notificationService.createNotificationRuleForDebt(userId, debt.getDebtName(), debt.getMinPayment(),
                                debt.getDebtId());
                return debt;
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
                // Update outstanding balance only if debt is newly activated or requested
                existingDebt.setPrincipalOutstanding(debtDTO.getPrincipalAmount());
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

}