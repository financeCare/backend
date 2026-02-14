package com.example.capstone.service;

import com.example.capstone.dto.*;
import com.example.capstone.entity.*;
import com.example.capstone.exception.BusinessException;
import com.example.capstone.repository.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
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
    private final RepaymentHistoryRepository repaymentHistoryRepository;

    public List<Debt> getOwnDebt(String token) {
        UUID userId = userService.extractUserIdFromToken(token);
        return debtRepository.findAllByUserId(userId);
    }

    public Debt getDebtDetail(String token,UUID debtId){
        UUID userId = userService.extractUserIdFromToken(token);
        return debtRepository.findByDebtIdAndUserId(debtId,userId).orElseThrow( () ->
               new BusinessException("Debt not found or not owned by this user", HttpStatus.NOT_FOUND)
        );
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
                        .anyMatch(c -> c.getType().equals("Expense"))
                )
                .toList();
        double income = budgetDTOs
                .stream()
                .filter(b -> categories
                        .stream()
                        .anyMatch(c -> c.getType().equals("Income"))
                )
                .mapToDouble(BudgetOverviewDto::getAmount)
                .sum();
        List<DebtGraphDTO> debtGraphDTOs = new ArrayList<>();
        for (Debt debt : debts) {
            debtGraphDTOs.add(new DebtGraphDTO(debt.getDebtName(), debt.getPrincipalAmount()));
        }
        return new OverviewGraphDTO(income, debtGraphDTOs, expense);
    }

    //TODO : fix bug from new field in entity Debt
    public Debt addDebt(String token, DebtDTO debtDTO) {
        UUID userId = userService.extractUserIdFromToken(token);
        if (debtDTO.getInterestRate() > 100 || debtDTO.getInterestRate() < 0) {
            throw new BusinessException("invalid interest rate number", HttpStatus.BAD_REQUEST);
        }
        Debt debt = new Debt();
        debt.setUserId(userId);
        debt.setPrincipalAmount(debtDTO.getPrincipalAmount());
        debt.setInterestRate(debtDTO.getInterestRate());
        debt.setRepaymentType(repaymentTypeRepository.findById(debtDTO.getRepaymentTypeId())
                .orElseThrow(() -> new BusinessException("invalid repayment type id", HttpStatus.BAD_REQUEST)));
        debt.setStartDate(debtDTO.getStartDate());
        debt.setEndDate(debtDTO.getEndDate());
        debt.setActive(true);
        debt.setPriority(debtDTO.getPriority());
        debt.setDebtType(debtTypeRepository.findById(debtDTO.getDebtTypeId())
                .orElseThrow(() -> new BusinessException("invalid debt type id", HttpStatus.BAD_REQUEST)));
        debt.setDebtName(debtDTO.getDebtName());
        debt.setMinPayment(debtDTO.getMinPayment());
        debt.setDueDay(debtDTO.getDueDay());
        System.out.println("getDueDay : " + debtDTO.getDueDay() + " before save");
        debtRepository.save(debt);
        System.out.println(debt.getDebtId() + " after save");
        notificationService.createNotificationRuleForDebt(userId, debt.getDebtName(), debt.getMinPayment(),debt.getDebtId());
        return debt;
    }

    public Debt updateDebt(String token, UUID debtId, DebtDTO debtDTO) {
        UUID userId = userService.extractUserIdFromToken(token);
        Debt existingDebt = debtRepository.findByDebtIdAndUserId(debtId,userId)
                .orElseThrow(() -> new BusinessException("Debt not found or not owned by this user", HttpStatus.NOT_FOUND));

        if (debtDTO.getInterestRate() > 100 || debtDTO.getInterestRate() < 0) {
            throw new BusinessException("invalid interest rate number", HttpStatus.BAD_REQUEST);
        }

        RepaymentType repaymentType = repaymentTypeRepository.findById(debtDTO.getRepaymentTypeId())
                .orElseThrow(() -> new BusinessException("invalid repaymentType id", HttpStatus.BAD_REQUEST));
        DebtType debtType = debtTypeRepository.findById(debtDTO.getDebtTypeId())
                .orElseThrow(() -> new BusinessException("invalid debtType id", HttpStatus.BAD_REQUEST));

        existingDebt.setPrincipalAmount(debtDTO.getPrincipalAmount());
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
        return debtRepository.save(existingDebt);
    }

    public Debt deleteDebt(String token, UUID debtId) {
        UUID userId = userService.extractUserIdFromToken(token);
         Debt debt = debtRepository.findByDebtIdAndUserId(debtId, userId)
                .orElseThrow(() -> new BusinessException("Debt not found or not owned by this user", HttpStatus.NOT_FOUND));
         debtRepository.delete(debt);
         return debt;
    }

    @Transactional
    public DebtPaymentResponseDTO payDebt(String token, DebtPaymentRequestDTO req) {
        UUID userId = userService.extractUserIdFromToken(token);
        Debt debt = debtRepository.findById(req.getDebtId())
                .orElseThrow(() -> new BusinessException("Debt not found", HttpStatus.NOT_FOUND));
        // ✅ กันจ่ายหนี้คนอื่น
        if (!debt.getUserId().equals(userId)) {
            throw new BusinessException("Forbidden", HttpStatus.FORBIDDEN);
        }
        // ✅ กันจ่ายหนี้ที่ปิดแล้ว
        if (!debt.isActive()) {
            throw new BusinessException("Debt is already closed", HttpStatus.BAD_REQUEST);
        }
        double amount = req.getAmount();
        if (amount <= 0) {
            throw new BusinessException("Amount must be > 0", HttpStatus.BAD_REQUEST);
        }
        double before = debt.getPrincipalAmount();
        // ✅ จ่ายเกินยอดได้ แต่ตัดให้ไม่ติดลบ (หรือจะเก็บเป็น overpay ก็ได้)
        double paid = Math.min(amount, before);
        debt.setPrincipalAmount(before - paid);
        boolean closed = debt.getPrincipalAmount() <= 0.000001;
        if (closed) {
            debt.setActive(false);
            debt.setPrincipalAmount(0.0);
            debt.setEndDate(req.getPaidAt() != null ? req.getPaidAt() : new Date());
        }
        debtRepository.save(debt);
        // ✅ บันทึกประวัติ
        RepaymentHistory history = new RepaymentHistory();
        history.setUserId(userId);
        history.setDebtId(debt.getDebtId());
        history.setAmountPaid(paid);
        history.setPaidDate(req.getPaidAt() != null ? req.getPaidAt() : new Date());
        repaymentHistoryRepository.save(history);
        return new DebtPaymentResponseDTO(
                debt.getDebtId(),
                paid,
                before,
                debt.getPrincipalAmount(),
                closed
        );
    }

}