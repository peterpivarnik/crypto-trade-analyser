package com.psw.cta.service.factory;

import com.psw.cta.entity.Statistic;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class StatisticFactory {

    public Statistic create(Long createdAt, Long all, Long valid, BigDecimal probability) {
        Statistic statistic = new Statistic();
        statistic.setCreatedAt(createdAt);
        statistic.setAll(all);
        statistic.setValid(valid);
        statistic.setProbability1h(probability);
        return statistic;
    }
}
