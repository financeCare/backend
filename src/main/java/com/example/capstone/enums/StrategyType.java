package com.example.capstone.enums;

public enum StrategyType {
    SNOWBALL,
    AVALANCHE,
    MINIMUM_ONLY,
    OPTIMAL_COST;

    public static StrategyType fromString(String text) {
        if (text == null) return null;
        try {
            // Support both "SNOWBALL" and "Snowball Strategy" styles
            String normalized = text.toUpperCase().trim().replace(" ", "_");
            if (normalized.endsWith("_STRATEGY")) {
                normalized = normalized.substring(0, normalized.length() - 9);
            }
            return StrategyType.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown strategy type: " + text);
        }
    }
}
