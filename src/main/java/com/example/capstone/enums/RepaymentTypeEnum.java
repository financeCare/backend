package com.example.capstone.enums;

import lombok.Getter;

@Getter
public enum RepaymentTypeEnum {
    EMI("Installment / EMI"),
    BULLET("Bullet Payment"),
    REVOLVING("Revolving / Credit Line"),
    LUMPSUM("Full Payment / Lump Sum");

    private final String dbValue;

    RepaymentTypeEnum(String dbValue) {
        this.dbValue = dbValue;
    }

    public static RepaymentTypeEnum fromString(String text) {
        if (text == null) return null;
        
        // Try exact match with enum name
        try {
            return RepaymentTypeEnum.valueOf(text.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            // Fallback to dbValue matching
            for (RepaymentTypeEnum type : RepaymentTypeEnum.values()) {
                if (type.dbValue.equalsIgnoreCase(text.trim())) {
                    return type;
                }
            }
        }
        
        // Final attempt: search for keywords
        String upperText = text.toUpperCase();
        if (upperText.contains("REVOLVING")) return REVOLVING;
        if (upperText.contains("EMI") || upperText.contains("INSTALLMENT")) return EMI;
        if (upperText.contains("BULLET")) return BULLET;
        if (upperText.contains("LUMP") || upperText.contains("FULL")) return LUMPSUM;

        throw new IllegalArgumentException("Unknown repayment type: " + text);
    }
}