package com.psw.cta.service.factory;

import com.psw.cta.entity.Statistic;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Component
public class StatisticFactory {

    public Statistic create(Long createdAt, Long all, Long valid, BigDecimal probability) {
        Statistic statistic = new Statistic();
        statistic.setCreatedAt(createdAt);
        statistic.setCreatedAtDate(LocalDateTime.ofInstant(Instant.ofEpochMilli(createdAt),
                                                           ZoneId.of("Europe/Vienna")));
        statistic.setAll(all);
        statistic.setValid(valid);
        statistic.setProbability1h(probability);
        return statistic;
    }
}
