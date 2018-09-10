package com.psw.cta.service.dto;

import lombok.Getter;
import lombok.ToString;

import java.math.BigDecimal;

@Getter
@ToString
public class AverageProfit {

    private BigDecimal average2H;
    private BigDecimal average5H;
    private BigDecimal average10H;
    private BigDecimal average24H;

    public AverageProfit(BigDecimal average2H, BigDecimal average5H, BigDecimal average10H, BigDecimal average24H) {
        this.average2H = average2H;
        this.average5H = average5H;
        this.average10H = average10H;
        this.average24H = average24H;
    }
}
