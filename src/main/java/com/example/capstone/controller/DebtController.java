package com.example.capstone.controller;

import com.example.capstone.dto.DebtDTO;
import com.example.capstone.dto.DebtPaymentRequestDTO;

import com.example.capstone.entity.Debt;
import com.example.capstone.service.DebtService;
import com.example.capstone.service.RepaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/debts")
@RequiredArgsConstructor
public class DebtController {

    private final DebtService debtService;
    private final RepaymentService repaymentService;

    @GetMapping
    public ResponseEntity<List<com.example.capstone.dto.DebtResponseDTO>> getOwnDebt(@RequestHeader("Authorization") String authorizationHeader) {
        String token = authorizationHeader.replace("Bearer ", "");
        List<com.example.capstone.dto.DebtResponseDTO> debt = debtService.getOwnDebt(token);
        return ResponseEntity.ok(debt);
    }

    @GetMapping("/{debtId}")
    public ResponseEntity<Debt> getDebtDetail(@RequestHeader("Authorization") String authorizationHeader,@PathVariable(name = "debtId") String debtId){
        String token = authorizationHeader.replace("Bearer ", "");
        return ResponseEntity.ok(debtService.getDebtDetail(token, UUID.fromString(debtId)));
    }

    @GetMapping("/overview-graph")
    public ResponseEntity<?> getOwnDebtGraph(@RequestHeader("Authorization") String authorizationHeader) {
        String token = authorizationHeader.replace("Bearer ", "");
        var debtGraph = debtService.getOverviewGraph(token);
        return ResponseEntity.ok(debtGraph);
    }

    @PostMapping()
    public ResponseEntity<Debt> createDebt(@RequestHeader("Authorization") String authorizationHeader,@Valid @RequestBody DebtDTO debtDTO) {
        String token = authorizationHeader.replace("Bearer ", "");
        Debt created = debtService.addDebt(token,debtDTO);
        return ResponseEntity.status(201).body(created);
    }

    @PutMapping("/{debtId}")
    public ResponseEntity<Debt> updateDebt(@RequestHeader("Authorization") String authorizationHeader ,@PathVariable(name = "debtId") String debtId,@Valid @RequestBody DebtDTO debtDTO) {
        String token = authorizationHeader.replace("Bearer ", "");
        Debt updated = debtService.updateDebt(token,UUID.fromString(debtId), debtDTO);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{debtId}")
    public ResponseEntity<Debt> deleteDebt(@RequestHeader("Authorization") String authorizationHeader ,@PathVariable(name = "debtId") String debtId) {
        String token = authorizationHeader.replace("Bearer ", "");
        Debt debt = debtService.deleteDebt(token,UUID.fromString(debtId));
        return ResponseEntity.ok(debt);
    }

    @PostMapping("/pays")
    public void payDebt(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody DebtPaymentRequestDTO debtPaymentRequestDTO
    ) {
        String token = authHeader.replace("Bearer ", "");
        repaymentService.payDebt(token, debtPaymentRequestDTO);
    }
    @GetMapping("/priorities")
    public ResponseEntity<List<com.example.capstone.dto.DebtPriorityResponseDTO>> getDebtPriorities(@RequestHeader("Authorization") String authorizationHeader) {
        String token = authorizationHeader.replace("Bearer ", "");
        return ResponseEntity.ok(debtService.getDebtPriorities(token));
    }

    @PutMapping("/priorities")
    public ResponseEntity<Void> updateDebtPriorities(
            @RequestHeader("Authorization") String authorizationHeader,
            @Valid @RequestBody List<com.example.capstone.dto.DebtPriorityUpdateRequestDTO> requests) {
        String token = authorizationHeader.replace("Bearer ", "");
        debtService.updateDebtPriorities(token, requests);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{debtId}/summary")
    public ResponseEntity<com.example.capstone.dto.DebtSummaryDTO> getDebtSummary(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable(name = "debtId") String debtId) {
        String token = authorizationHeader.replace("Bearer ", "");
        return ResponseEntity.ok(debtService.getDebtSummary(token, UUID.fromString(debtId)));
    }

}