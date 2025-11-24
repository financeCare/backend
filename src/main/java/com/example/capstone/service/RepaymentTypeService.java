package com.example.capstone.service;

import com.example.capstone.entity.RepaymentType;
import com.example.capstone.exception.BusinessException;
import com.example.capstone.repository.RepaymentTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RepaymentTypeService {
    private final RepaymentTypeRepository repaymentTypeRepository;

    public List<RepaymentType> getAllRepaymentTypes() {
        return repaymentTypeRepository.findAll();
    }

    public String addRepaymentType(RepaymentType repaymentType) {
        try {
            repaymentTypeRepository.save(repaymentType);
        } catch (Exception e) {
            throw new BusinessException(e.getMessage(), HttpStatus.NOT_FOUND);
        }
        return "repaymentType add successfully";
    }

    public String deleteRepaymentType(Integer repaymentTypeId) {
        repaymentTypeRepository.findById(repaymentTypeId).ifPresent(repaymentTypeRepository::delete);
        return "repaymentType id " + repaymentTypeId + " delete successfully";
    }
}