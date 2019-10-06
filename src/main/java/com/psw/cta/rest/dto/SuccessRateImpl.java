package com.psw.cta.rest.dto;

import lombok.Getter;
import lombok.ToString;

@ToString
@Getter
public class SuccessRateImpl implements SuccessRate {

    private double oneDaySuccessRate;
    private double twoDaysSuccessRate;
    private double oneWeekSuccessRate;

    public SuccessRateImpl(double oneDaySuccessRate, double twoDaysSuccessRate, double oneWeekSuccessRate) {
        this.oneDaySuccessRate = oneDaySuccessRate;
        this.twoDaysSuccessRate = twoDaysSuccessRate;
        this.oneWeekSuccessRate = oneWeekSuccessRate;
    }
}
