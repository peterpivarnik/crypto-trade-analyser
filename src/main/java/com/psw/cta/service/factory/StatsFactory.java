package com.psw.cta.service.factory;

import com.psw.cta.rest.dto.Stats;
import org.springframework.stereotype.Component;

@Component
public class StatsFactory {

    public Stats create(double oneDayStats, double oneWeekStats, double oneMonthStats) {
        Stats stats = new Stats();
        stats.setOneDayStats(oneDayStats);
        stats.setOneWeekStats(oneWeekStats);
        stats.setOneMonthStats(oneMonthStats);
        return stats;
    }
}
