package com.psw.cta.rest.dto;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class CompleteStatsImpl implements CompleteStats {

    private Stats Stats2H;
    private Stats Stats5H;

    public CompleteStatsImpl(Stats stats2H, Stats stats5H) {
        Stats2H = stats2H;
        Stats5H = stats5H;
    }
}
