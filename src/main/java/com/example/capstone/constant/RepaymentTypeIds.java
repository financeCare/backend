package com.example.capstone.constant;

import java.util.Set;

public final class RepaymentTypeIds {
    private RepaymentTypeIds() {}

    public static final int LUMP_SUM = 5;
    public static final int EMI = 6;
    public static final int REVOLVING = 7;
    public static final int BULLET = 8;

    public static final Set<Integer> SUPPORTED = Set.of(LUMP_SUM, EMI, REVOLVING, BULLET);
}
