package com.example.capstone.controller;

import com.example.capstone.dto.PlanResultDTO;
import com.example.capstone.dto.RepaymentPlanDTO;
import com.example.capstone.dto.RepaymentStrategyDTO;
import com.example.capstone.dto.RepaymentStrategyDtoResponse;
import com.example.capstone.entity.RepaymentPlan;
import com.example.capstone.entity.RepaymentStrategy;
import com.example.capstone.service.RepaymentPlanService;
import com.example.capstone.service.UserService;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/repayment-plans")
@RequiredArgsConstructor
public class RepaymentPlanController {
    private static final String BEARER_PREFIX = "Bearer ";

    private final RepaymentPlanService repaymentPlanService;
    private final UserService userService;

    @PostMapping("/strategies")
    public ResponseEntity<RepaymentStrategy> createRepaymentStrategy(@Valid @RequestBody RepaymentStrategyDTO repaymentStrategyDTO) {
        return ResponseEntity.ok(repaymentPlanService.createRepaymentStrategy(repaymentStrategyDTO));
    }

    @GetMapping("/strategies")
    public ResponseEntity<RepaymentStrategyDtoResponse> getAllRepaymentStrategies(
            @RequestHeader("Authorization") String authorizationHeader) {
        String token = authorizationHeader.replace(BEARER_PREFIX, "");
        return ResponseEntity.ok(repaymentPlanService.getAllRepaymentStrategies(token));
    }

    @DeleteMapping("/strategies/{id}")
    public ResponseEntity<String> deleteRepaymentStrategy(@PathVariable(name = "id") String strategyId) {
        return ResponseEntity.ok(repaymentPlanService.deleteRepaymentStrategy(UUID.fromString(strategyId)));
    }

    @PostMapping()
    public ResponseEntity<RepaymentPlan> createRepaymentPlan(@RequestHeader("Authorization") String authorizationHeader,
            @Valid @RequestBody RepaymentPlanDTO repaymentPlanDTO) {
        String token = authorizationHeader.replace(BEARER_PREFIX, "");
        return ResponseEntity.ok(repaymentPlanService.changeRepaymentPlan(userService.extractUserIdFromToken(token),
                repaymentPlanDTO.getMonthlyBudget(), repaymentPlanDTO.getStrategyId()));
    }

    @GetMapping("/total-min-payment")
    public ResponseEntity<Map<String, BigDecimal>> getTotalMinPayment(@RequestHeader("Authorization") String authorizationHeader) {
        String token = authorizationHeader.replace(BEARER_PREFIX, "");
        return ResponseEntity.ok(Map.of("totalMinPayment", repaymentPlanService.getTotalMinPayment(token)));
    }

    @GetMapping()
    public ResponseEntity<PlanResultDTO> simulateDebtRepayment(@RequestHeader("Authorization") String authorizationHeader) {
        String token = authorizationHeader.replace(BEARER_PREFIX, "");
        return ResponseEntity.ok(repaymentPlanService.simulate(token));
    }

    @GetMapping("/priority-suggestion")
    public ResponseEntity<List<Map<String, Object>>> getPrioritySuggestion(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestParam(name = "strategyId") UUID strategyId) {
        String token = authorizationHeader.replace(BEARER_PREFIX, "");
        return ResponseEntity.ok(repaymentPlanService.getPrioritySuggestions(token, strategyId));
    }

}