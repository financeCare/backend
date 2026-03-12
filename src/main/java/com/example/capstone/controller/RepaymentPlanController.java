package com.example.capstone.controller;

import com.example.capstone.dto.RepaymentPlanDTO;
import com.example.capstone.dto.RepaymentStrategyDTO;
import com.example.capstone.dto.RepaymentStrategyDtoResponse;
import com.example.capstone.service.RepaymentPlanService;
import com.example.capstone.service.UserService;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;

@RestController
@RequestMapping("/repayment-plans")
@RequiredArgsConstructor
public class RepaymentPlanController {

    private final RepaymentPlanService repaymentPlanService;
    private final UserService userService;

    @PostMapping("/strategies")
    public ResponseEntity<?> createRepaymentStrategy(@Valid @RequestBody RepaymentStrategyDTO repaymentStrategyDTO) {
        return ResponseEntity.ok(repaymentPlanService.createRepaymentStrategy(repaymentStrategyDTO));
    }

    @GetMapping("/strategies")
    public ResponseEntity<RepaymentStrategyDtoResponse> getAllRepaymentStrategies(
            @RequestHeader("Authorization") String authorizationHeader) {
        String token = authorizationHeader.replace("Bearer ", "");
        return ResponseEntity.ok(repaymentPlanService.getAllRepaymentStrategies(token));
    }

    @DeleteMapping("/strategies/{id}")
    public ResponseEntity<?> deleteRepaymentStrategy(@PathVariable(name = "id") String strategyId) {
        return ResponseEntity.ok(repaymentPlanService.deleteRepaymentStrategy(UUID.fromString(strategyId)));
    }

    @PostMapping()
    public ResponseEntity<?> createRepaymentPlan(@RequestHeader("Authorization") String authorizationHeader,
            @Valid @RequestBody RepaymentPlanDTO repaymentPlanDTO) {
        String token = authorizationHeader.replace("Bearer ", "");
        return ResponseEntity.ok(repaymentPlanService.changeRepaymentPlan(userService.extractUserIdFromToken(token),
                repaymentPlanDTO.getMonthlyBudget(), repaymentPlanDTO.getStrategyId()));
    }

    @GetMapping("/total-min-payment")
    public ResponseEntity<?> getTotalMinPayment(@RequestHeader("Authorization") String authorizationHeader) {
        String token = authorizationHeader.replace("Bearer ", "");
        return ResponseEntity.ok(java.util.Map.of("totalMinPayment", repaymentPlanService.getTotalMinPayment(token)));
    }

    @GetMapping()
    public ResponseEntity<?> simulateDebtRepayment(@RequestHeader("Authorization") String authorizationHeader) {
        String token = authorizationHeader.replace("Bearer ", "");
        return ResponseEntity.ok(repaymentPlanService.simulate(token));
    }

    @GetMapping("/priority-suggestion")
    public ResponseEntity<?> getPrioritySuggestion(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestParam(name = "strategyId") UUID strategyId) {
        String token = authorizationHeader.replace("Bearer ", "");
        return ResponseEntity.ok(repaymentPlanService.getPrioritySuggestions(token, strategyId));
    }

}