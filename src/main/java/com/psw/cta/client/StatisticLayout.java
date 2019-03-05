package com.psw.cta.client;

import com.psw.cta.rest.dto.Stats;
import com.psw.cta.service.CacheService;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.spring.annotation.SpringComponent;

import static com.psw.cta.client.ClientUtils.createTextField;

@SpringComponent
public class StatisticLayout{

    private CacheService cacheService;

    public StatisticLayout(CacheService cacheService) {
        this.cacheService = cacheService;
    }

    public HorizontalLayout getLayout() {
        return new HorizontalLayout(getStatisticsLayout(cacheService.getCompleteStats().getStats()));
    }

    private VerticalLayout getStatisticsLayout(Stats stats) {
        return new VerticalLayout(getStatisticsDetailLayout(stats));
    }

    private HorizontalLayout getStatisticsDetailLayout(Stats stats) {
        VerticalLayout lastMonthStatsLayout = createStatsLayout("Last day", stats.getOneDayStats());
        VerticalLayout lastWeekStatsLayout = createStatsLayout("Last week", stats.getOneWeekStats());
        VerticalLayout lastDayStatsLayout = createStatsLayout("Last month", stats.getOneMonthStats());
        return new HorizontalLayout(lastMonthStatsLayout, lastWeekStatsLayout, lastDayStatsLayout);
    }

    private VerticalLayout createStatsLayout(String labelName, double stats) {
        TextField textField = createTextField(labelName, String.valueOf(stats));
        textField.setReadOnly(true);
        return new VerticalLayout(textField);
    }
}
