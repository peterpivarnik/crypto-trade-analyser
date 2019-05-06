package com.psw.cta.client.service;

import com.psw.cta.client.factory.ClientFactory;
import com.psw.cta.rest.dto.Stats;
import com.psw.cta.service.CacheService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.spring.annotation.SpringComponent;

@SpringComponent
public class StatisticsService {

    private CacheService cacheService;
    private ClientFactory clientFactory;

    public StatisticsService(CacheService cacheService, ClientFactory clientFactory) {
        this.cacheService = cacheService;
        this.clientFactory = clientFactory;
    }

    VerticalLayout getStatisticLayout() {
        Component layoutLabel = clientFactory.createLayoutLabel("Statistics", "100%");
        VerticalLayout statisticFieldsLayout = getStatisticFieldsLayout(cacheService.getCompleteStats().getStats());
        return clientFactory.createVerticalLayout(layoutLabel, statisticFieldsLayout);
    }

    private VerticalLayout getStatisticFieldsLayout(Stats stats) {
        TextField lastMonthStatsTextField = clientFactory.createTextField("Last day",
                                                                          String.valueOf(stats.getOneDayStats()));
        TextField lastWeekStatsTextField = clientFactory.createTextField("Last week",
                                                                         String.valueOf(stats.getOneWeekStats()));
        TextField lastDayStatsTextField = clientFactory.createTextField("Last month",
                                                                        String.valueOf(stats.getOneMonthStats()));
        return clientFactory.createVerticalLayout(lastMonthStatsTextField,
                                                  lastWeekStatsTextField,
                                                  lastDayStatsTextField);
    }
}
