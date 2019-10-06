package com.psw.cta.service.factory;

import com.psw.cta.rest.dto.SuccessRate;
import com.psw.cta.rest.dto.SuccessRateImpl;
import org.springframework.stereotype.Component;

@Component
public class SuccessRateFactory {

    public SuccessRate create(double oneDaySuccessRate, double twoDaysSuccessRate, double oneWeekSuccessRate) {
        return new SuccessRateImpl(oneDaySuccessRate, twoDaysSuccessRate, oneWeekSuccessRate);
    }
}
