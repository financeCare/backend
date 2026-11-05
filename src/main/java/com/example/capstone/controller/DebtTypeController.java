package com.example.capstone.controller;

import com.example.capstone.dto.DebtTypeRequest;
import com.example.capstone.entity.DebtType;
import com.example.capstone.security.AdminAccess;
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
    private final AdminAccess adminAccess;

    @GetMapping
    public List<DebtType> getAllDebtTypes() {
        return debtTypeService.getAllDebtTypes();
    }

    @PostMapping
    public ResponseEntity<String> addDebtType(@RequestHeader("Authorization") String authorizationHeader, @Valid @RequestBody DebtTypeRequest request) {
        adminAccess.requireAdmin(authorizationHeader);
        String msg = debtTypeService.addDebtType(request);
        return ResponseEntity.status(201).body(msg);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteDebtType(@RequestHeader("Authorization") String authorizationHeader, @PathVariable("id") Integer id) {
        adminAccess.requireAdmin(authorizationHeader);
        String msg = debtTypeService.deleteDebtType(id);
        return ResponseEntity.ok(msg);
    }

}
