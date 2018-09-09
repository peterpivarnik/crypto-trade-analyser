package com.psw.cta.rest.dto;

import lombok.Getter;
import lombok.ToString;

@ToString
@Getter
public class StatsImpl implements Stats {

    private double oneDayStats;
    private double oneWeekStats;
    private double oneMonthStats;

    public StatsImpl(double oneDayStats, double oneWeekStats, double oneMonthStats) {
        this.oneDayStats = oneDayStats;
        this.oneWeekStats = oneWeekStats;
        this.oneMonthStats = oneMonthStats;
    }
}
