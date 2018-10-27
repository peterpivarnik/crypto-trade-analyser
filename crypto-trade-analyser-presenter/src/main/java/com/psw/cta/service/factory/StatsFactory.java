package com.psw.cta.service.factory;

import com.psw.cta.rest.dto.Stats;
import com.psw.cta.rest.dto.StatsImpl;
import org.springframework.stereotype.Component;

@Component
public class StatsFactory {

    public Stats create(double oneDayStats, double oneWeekStats, double oneMonthStats) {
        return new StatsImpl(oneDayStats, oneWeekStats, oneMonthStats);
    }
}
