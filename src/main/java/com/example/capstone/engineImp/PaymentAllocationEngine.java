package com.example.capstone.engineImp;

import com.example.capstone.domain.PaymentAllocationResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class PaymentAllocationEngine {

    public PaymentAllocationResult allocate(
            BigDecimal payment,
            BigDecimal interest,
            BigDecimal penalty,
            BigDecimal principal
    ) {

        BigDecimal remaining = payment;

        BigDecimal paidPenalty = remaining.min(penalty);
        remaining = remaining.subtract(paidPenalty);

        BigDecimal paidInterest = remaining.min(interest);
        remaining = remaining.subtract(paidInterest);

        BigDecimal paidPrincipal = remaining.min(principal);

        return new PaymentAllocationResult(
                paidPenalty,
                paidInterest,
                paidPrincipal
        );
    }
}