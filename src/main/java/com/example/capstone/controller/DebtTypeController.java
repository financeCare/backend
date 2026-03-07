package com.example.capstone.controller;

import com.example.capstone.entity.DebtType;
import com.example.capstone.service.DebtTypeService;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/debt-types")
@RequiredArgsConstructor
public class DebtTypeController {

    private final DebtTypeService debtTypeService;

    @GetMapping
    public List<DebtType> getAllDebtTypes() {
        return debtTypeService.getAllDebtTypes();
    }

    @PostMapping
    public ResponseEntity<String> addDebtType(@Valid @RequestBody DebtType debtType) {
        String msg = debtTypeService.addDebtType(debtType);
        return ResponseEntity.status(201).body(msg);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteDebtType(@PathVariable Integer id) {
        String msg = debtTypeService.deleteDebtType(id);
        return ResponseEntity.ok(msg);
    }

}