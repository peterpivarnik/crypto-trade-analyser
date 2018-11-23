package com.psw.cta.service.factory;

import com.psw.cta.rest.dto.CompleteStats;
import com.psw.cta.rest.dto.CompleteStatsImpl;
import com.psw.cta.rest.dto.Stats;
import org.springframework.stereotype.Component;

@Component
public class CompleteStatsFactory {

    public CompleteStats createCompleteStats(Stats stats2h, Stats stats5h) {
        return new CompleteStatsImpl(stats2h, stats5h);

    }
}
