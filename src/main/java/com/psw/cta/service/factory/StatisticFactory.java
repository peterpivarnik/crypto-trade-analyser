package com.psw.cta.service.factory;

import com.psw.cta.entity.Statistic;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class StatisticFactory {

    public Statistic create(Long createdAt, BigDecimal probability) {
        Statistic statistic = new Statistic();
        statistic.setCreatedAt(createdAt);
        statistic.setProbability1h(probability);
        return statistic;
    }
}
