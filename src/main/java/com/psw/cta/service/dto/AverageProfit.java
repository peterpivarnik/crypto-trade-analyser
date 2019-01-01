package com.psw.cta.service.dto;

import lombok.Getter;
import lombok.ToString;

import java.math.BigDecimal;

@Getter
@ToString
public class AverageProfit {

    private BigDecimal average1H;

    public AverageProfit(BigDecimal average1H) {
        this.average1H = average1H;
    }
}
