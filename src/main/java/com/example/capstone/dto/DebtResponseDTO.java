package com.example.capstone.dto;

import com.example.capstone.entity.Debt;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DebtResponseDTO {
    private Debt debt;
    private DebtSummaryDTO summary;
}
