package com.example.capstone.controller;

import com.example.capstone.entity.RepaymentType;
import com.example.capstone.service.RepaymentTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/repayment-types")
@RequiredArgsConstructor
public class RepaymentTypeController {

    private final RepaymentTypeService repaymentTypeService;

    @GetMapping
    public List<RepaymentType> getAllRepaymentTypes() {
        return repaymentTypeService.getAllRepaymentTypes();
    }

    @PostMapping
    public ResponseEntity<String> addRepaymentType(@RequestBody RepaymentType repaymentType) {
        String msg = repaymentTypeService.addRepaymentType(repaymentType);
        return ResponseEntity.status(201).body(msg);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteRepaymentType(@PathVariable Integer id) {
        String msg = repaymentTypeService.deleteRepaymentType(id);
        return ResponseEntity.ok(msg);
    }

}