package com.casino.casinoerp.entity;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public enum ChipDenomination {
    NPR_500(500),
    NPR_1000(1000),
    NPR_5000(5000),
    NPR_10000(10000),
    NPR_25000(25000);

    private final int value;

    ChipDenomination(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }

    public BigDecimal monetaryValue() {
        return BigDecimal.valueOf(value);
    }

    public static boolean supports(Integer value) {
        return value != null && Arrays.stream(values()).anyMatch(item -> item.value == value);
    }

    public static Set<Integer> supportedValues() {
        return Arrays.stream(values()).map(ChipDenomination::value).collect(Collectors.toUnmodifiableSet());
    }
}
