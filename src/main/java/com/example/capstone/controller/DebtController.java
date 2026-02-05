package com.example.capstone.controller;

import com.example.capstone.dto.DebtDTO;
import com.example.capstone.entity.Debt;
import com.example.capstone.service.DebtService;
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

    @GetMapping()
    public ResponseEntity<List<Debt>> getOwnDebt(@RequestHeader("Authorization") String authorizationHeader) {
        String token = authorizationHeader.replace("Bearer ", "");
        List<Debt> debt = debtService.getOwnDebt(token);
        return ResponseEntity.ok(debt);
    }

    @GetMapping("/{debtId}")
    public ResponseEntity<Debt> getDebtDetail(@RequestHeader("Authorization") String authorizationHeader,@PathVariable String debtId){
        String token = authorizationHeader.replace("Bearer ", "");
        return ResponseEntity.ok(debtService.getDebtDetail(token, UUID.fromString(debtId)));
    }

    @GetMapping("/overview-graph")
    public ResponseEntity<?> getOwnDebtGraph(@RequestHeader("Authorization") String authorizationHeader) {
        String token = authorizationHeader.replace("Bearer ", "");
        var debtGraph = debtService.getOverviewGraph(token);
        return ResponseEntity.ok(debtGraph);
    }

    @PostMapping
    public ResponseEntity<Debt> createDebt(@RequestHeader("Authorization") String authorizationHeader,@RequestBody DebtDTO debtDTO) {
        String token = authorizationHeader.replace("Bearer ", "");
        Debt created = debtService.addDebt(token,debtDTO);
        return ResponseEntity.status(201).body(created);
    }

    @PutMapping("/{debtId}")
    public ResponseEntity<Debt> updateDebt(@RequestHeader("Authorization") String authorizationHeader ,@PathVariable String debtId, @RequestBody DebtDTO debtDTO) {
        String token = authorizationHeader.replace("Bearer ", "");
        Debt updated = debtService.updateDebt(token,UUID.fromString(debtId), debtDTO);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{debtId}")
    public ResponseEntity<Debt> deleteDebt(@RequestHeader("Authorization") String authorizationHeader ,@PathVariable String debtId) {
        String token = authorizationHeader.replace("Bearer ", "");
        Debt debt = debtService.deleteDebt(token,UUID.fromString(debtId));
        return ResponseEntity.ok(debt);
    }
}