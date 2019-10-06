package com.psw.cta.rest.dto;

import org.immutables.value.Value;

@Value.Immutable
public interface SuccessRate {

    double getOneDaySuccessRate();

    double getTwoDaysSuccessRate();

    double getOneWeekSuccessRate();
}
