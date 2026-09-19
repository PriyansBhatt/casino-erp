package com.casino.casinoerp.service;

import com.casino.casinoerp.dto.AccountsDtos.Invoice;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** Explicit invoice amounts only; no tax-rate, valuation or recognition policy. */
public final class AccountsAmounts {
    private AccountsAmounts() {}
    public static BigDecimal amount(BigDecimal value) {
        if(value==null || value.signum()<0 || value.compareTo(new BigDecimal("999999999999.99"))>0)
            throw new IllegalArgumentException("NPR amounts must be nonnegative and at most 999999999999.99.");
        try { return value.setScale(2,RoundingMode.UNNECESSARY); }
        catch(ArithmeticException e) { throw new IllegalArgumentException("Amounts must be exact to NPR paisa; no rounding is performed."); }
    }
    public static void validate(Invoice i) {
        if(i==null || !"NPR".equals(i.currency()) || i.lines()==null || i.lines().isEmpty() || i.lines().size()>100)
            throw new IllegalArgumentException("An NPR invoice with 1–100 lines is required.");
        BigDecimal sum=BigDecimal.ZERO;
        for(var line:i.lines()) {
            if(line==null || line.description()==null || line.description().isBlank() || line.description().length()>300)
                throw new IllegalArgumentException("Each invoice line requires a description of at most 300 characters.");
            sum=sum.add(amount(line.amount()));
        }
        if(sum.compareTo(amount(i.subtotal()))!=0 || amount(i.subtotal()).subtract(amount(i.discount())).add(amount(i.tax())).compareTo(amount(i.total()))!=0)
            throw new IllegalArgumentException("Line sum must equal subtotal; total must equal subtotal minus discount plus tax.");
    }
}
