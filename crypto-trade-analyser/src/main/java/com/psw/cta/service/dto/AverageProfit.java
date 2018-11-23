package com.psw.cta.service.dto;

import lombok.Getter;
import lombok.ToString;

import java.math.BigDecimal;

@Getter
@ToString
public class AverageProfit {

    private BigDecimal average2H;
    private BigDecimal average5H;

    public AverageProfit(BigDecimal average2H, BigDecimal average5H) {
        this.average2H = average2H;
        this.average5H = average5H;
    }
}
