package com.psw.cta.rest.dto;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class CompleteStatsImpl implements CompleteStats {

    private Stats stats;

    public CompleteStatsImpl(Stats stats) {
        this.stats = stats;
    }
}
