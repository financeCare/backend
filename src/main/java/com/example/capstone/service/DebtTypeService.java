package com.example.capstone.service;

import com.example.capstone.entity.DebtType;
import com.example.capstone.repository.DebtTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DebtTypeService {
    private final DebtTypeRepository debtTypeRepository;

    public List<DebtType> getAllDebtTypes() {
        return debtTypeRepository.findAll();
    }

    public String addDebtType(DebtType debtType) {
        debtTypeRepository.save(debtType);
        return "debtType add successfully";
    }

    public String deleteDebtType(Integer debtId) {
        debtTypeRepository.findById(debtId).ifPresent(debtTypeRepository::delete);
        return "debtType id " + debtId + " delete successfully";
    }
}