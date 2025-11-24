package com.example.capstone.service;

import com.example.capstone.dto.DebtDTO;
import com.example.capstone.entity.Debt;
import com.example.capstone.entity.DebtType;
import com.example.capstone.entity.RepaymentType;
import com.example.capstone.exception.BusinessException;
import com.example.capstone.repository.DebtRepository;
import com.example.capstone.repository.DebtTypeRepository;
import com.example.capstone.repository.RepaymentHistoryRepository;
import com.example.capstone.repository.RepaymentTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DebtService {
    private final DebtTypeRepository debtTypeRepository;
    private final DebtRepository debtRepository;
    private final RepaymentTypeRepository repaymentTypeRepository;
    private final UserService userService;

    public List<Debt> getOwnDebts(String token) {
        UUID userId = userService.extractUserIdFromToken(token);
        return debtRepository.findAllByUserId(userId);
    }

    public Debt addDebt(String token, DebtDTO debtDTO) {
        UUID userId = userService.extractUserIdFromToken(token);
        if (debtDTO.getInterestRate() > 100 || debtDTO.getInterestRate() < 0) {
            throw new BusinessException("invalid interest rate number", HttpStatus.FORBIDDEN);
        }
        Debt debt = new Debt();
        debt.setUserId(userId);
        debt.setPrincipalAmount(debtDTO.getPrincipalAmount());
        debt.setInterestRate(debtDTO.getInterestRate());
        debt.setRepaymentType(repaymentTypeRepository.findById(debtDTO.getRepaymentTypeId())
                .orElseThrow(() -> new BusinessException("invalid repayment type id", HttpStatus.BAD_REQUEST)));
        debt.setStartDate(debtDTO.getStartDate());
        debt.setEndDate(debtDTO.getEndDate());
        System.out.println(debtDTO.getIsActive());
        debt.setActive(debtDTO.getIsActive());
        debt.setPriority(debtDTO.getPriority());
        debt.setDebtType(debtTypeRepository.findById(debtDTO.getDebtTypeId())
                .orElseThrow(() -> new BusinessException("invalid debt type id", HttpStatus.BAD_REQUEST)));
        debt.setDebtName(debtDTO.getDebtName());
        return debtRepository.save(debt);
    }

    public Debt updateDebt(String token, Integer debtId, DebtDTO debtDTO) {
        UUID userId = userService.extractUserIdFromToken(token);
        Debt existingDebt = debtRepository.findByDebtIdAndUserId(debtId,userId)
                .orElseThrow(() -> new BusinessException("Debt not found or not owned by this user", HttpStatus.NOT_FOUND));

        if (debtDTO.getInterestRate() > 100 || debtDTO.getInterestRate() < 0) {
            throw new BusinessException("invalid interest rate number", HttpStatus.FORBIDDEN);
        }

        RepaymentType repaymentType = repaymentTypeRepository.findById(debtDTO.getRepaymentTypeId())
                .orElseThrow(() -> new BusinessException("invalid repaymentType id", HttpStatus.FORBIDDEN));
        DebtType debtType = debtTypeRepository.findById(debtDTO.getDebtTypeId())
                .orElseThrow(() -> new BusinessException("invalid debtType id", HttpStatus.FORBIDDEN));

        existingDebt.setPrincipalAmount(debtDTO.getPrincipalAmount());
        existingDebt.setInterestRate(debtDTO.getInterestRate());
        existingDebt.setRepaymentType(repaymentType);
        existingDebt.setStartDate(debtDTO.getStartDate());
        existingDebt.setEndDate(debtDTO.getEndDate());
        existingDebt.setActive(debtDTO.getIsActive());
        existingDebt.setPriority(debtDTO.getPriority());
        existingDebt.setDebtType(debtType);
        existingDebt.setDebtName(debtDTO.getDebtName());

        return debtRepository.save(existingDebt);
    }

    public Debt deleteDebt(String token, Integer debtId) {
        UUID userId = userService.extractUserIdFromToken(token);
         Debt debt = debtRepository.findByDebtIdAndUserId(debtId, userId)
                .orElseThrow(() -> new BusinessException("Debt not found or not owned by this user", HttpStatus.NOT_FOUND));
         debtRepository.delete(debt);
         return debt;
    }

}