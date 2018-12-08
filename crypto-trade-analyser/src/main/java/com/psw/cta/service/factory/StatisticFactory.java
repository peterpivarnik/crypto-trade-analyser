package com.psw.cta.service.factory;

import com.psw.cta.entity.Statistic;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class StatisticFactory {

    public Statistic create(Long createdAt, BigDecimal probability1h, BigDecimal probability2h, BigDecimal probability5h) {
        Statistic statistic = new Statistic();
        statistic.setCreatedAt(createdAt);
        statistic.setProbability1h(probability1h);
        statistic.setProbability2h(probability2h);
        statistic.setProbability5h(probability5h);
        return statistic;
    }
}
