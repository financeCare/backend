package com.example.capstone.enums;

public enum DebtTxnType {

    // เงินเข้าจริงจากลูกค้า
    PAYMENT,

    // การเกิด charge
    INTEREST_CHARGE,
    LATE_FEE_CHARGE,
    PENALTY_INTEREST_CHARGE,
    OVERPAYMENT_FEE_CHARGE,

    // การจ่ายเพื่อตัด charge
    INTEREST_PAYMENT,
    LATE_FEE_PAYMENT,
    PENALTY_INTEREST_PAYMENT,
    OVERPAYMENT_FEE_PAYMENT,
    PRINCIPAL_PAYMENT,

    // เงินเกิน
    OVERPAYMENT
}