package com.psw.cta.service.dto;

import lombok.Getter;
import lombok.ToString;

import java.math.BigDecimal;

@Getter
@ToString
public class AverageProfit {

    private BigDecimal average1H;
    private BigDecimal average2H;
    private BigDecimal average5H;

    public AverageProfit(BigDecimal average1H, BigDecimal average2H, BigDecimal average5H) {
        this.average1H = average1H;
        this.average2H = average2H;
        this.average5H = average5H;
    }
}
