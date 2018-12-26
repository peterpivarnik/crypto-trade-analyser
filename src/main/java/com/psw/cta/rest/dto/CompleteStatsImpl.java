package com.psw.cta.rest.dto;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class CompleteStatsImpl implements CompleteStats {

    private Stats stats1H;
    private Stats stats2H;
    private Stats stats5H;

    public CompleteStatsImpl(Stats stats1H, Stats stats2H, Stats stats5H) {
        this.stats1H = stats1H;
        this.stats2H = stats2H;
        this.stats5H = stats5H;
    }
}
