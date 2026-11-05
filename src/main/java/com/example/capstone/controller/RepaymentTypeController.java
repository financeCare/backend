package com.example.capstone.controller;

import com.example.capstone.dto.RepaymentTypeRequest;
import com.example.capstone.entity.RepaymentType;
import com.example.capstone.security.AdminAccess;
import com.example.capstone.service.RepaymentTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/repayment-types")
@RequiredArgsConstructor
public class RepaymentTypeController {

    private final RepaymentTypeService repaymentTypeService;
    private final AdminAccess adminAccess;

    @GetMapping
    public List<RepaymentType> getAllRepaymentTypes() {
        return repaymentTypeService.getAllRepaymentTypes();
    }

    @PostMapping
    public ResponseEntity<String> addRepaymentType(@RequestHeader("Authorization") String authorizationHeader, @RequestBody RepaymentTypeRequest request) {
        adminAccess.requireAdmin(authorizationHeader);
        String msg = repaymentTypeService.addRepaymentType(request);
        return ResponseEntity.status(201).body(msg);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteRepaymentType(@RequestHeader("Authorization") String authorizationHeader, @PathVariable("id") Integer id) {
        adminAccess.requireAdmin(authorizationHeader);
        String msg = repaymentTypeService.deleteRepaymentType(id);
        return ResponseEntity.ok(msg);
    }

}
