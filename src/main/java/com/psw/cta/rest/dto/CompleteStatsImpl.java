package com.psw.cta.rest.dto;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class CompleteStatsImpl implements CompleteStats {

    private Stats Stats2H;
    private Stats Stats5H;
    private Stats Stats10H;
    private Stats Stats24H;

    public CompleteStatsImpl(Stats stats2H, Stats stats5H, Stats stats10H, Stats stats24H) {
        Stats2H = stats2H;
        Stats5H = stats5H;
        Stats10H = stats10H;
        Stats24H = stats24H;
    }
}
