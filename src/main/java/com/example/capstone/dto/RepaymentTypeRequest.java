package com.example.capstone.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RepaymentTypeRequest {
    private String repaymentTypeName;
    private String repaymentTypeDescription;
}
