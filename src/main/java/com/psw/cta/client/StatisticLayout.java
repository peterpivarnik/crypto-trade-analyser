package com.psw.cta.client;

import com.psw.cta.rest.dto.Stats;
import com.psw.cta.service.CacheService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.dom.Style;
import com.vaadin.flow.spring.annotation.SpringComponent;

import static com.psw.cta.client.ClientUtils.createTextField;
import static com.psw.cta.client.ClientUtils.getLayoutLabel;
import static com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment.CENTER;

@SpringComponent
public class StatisticLayout {

    private CacheService cacheService;

    public StatisticLayout(CacheService cacheService) {
        this.cacheService = cacheService;
    }

    VerticalLayout getLayout() {
        Component layoutLabel = getLayoutLabel("Statistics", "100%");
        VerticalLayout statisticsLayout = getStatisticsLayout(cacheService.getCompleteStats().getStats());
        VerticalLayout verticalLayout = new VerticalLayout(layoutLabel, statisticsLayout);
        verticalLayout.setDefaultHorizontalComponentAlignment(CENTER);
        Style style = verticalLayout.getStyle();
        style.set("background-color", "var(--lumo-contrast-10pct)");
        return verticalLayout;
    }

    private VerticalLayout getStatisticsLayout(Stats stats) {
        TextField lastMonthStatsTextField = createStatsLayout("Last day", stats.getOneDayStats());
        TextField lastWeekStatsTextField = createStatsLayout("Last week", stats.getOneWeekStats());
        TextField lastDayStatsTextField = createStatsLayout("Last month", stats.getOneMonthStats());
        VerticalLayout verticalLayout = new VerticalLayout(lastMonthStatsTextField,
                                                           lastWeekStatsTextField,
                                                           lastDayStatsTextField);
        verticalLayout.setDefaultHorizontalComponentAlignment(CENTER);
        return verticalLayout;
    }

    private TextField createStatsLayout(String labelName, double stats) {
        TextField textField = createTextField(labelName, String.valueOf(stats));
        textField.setReadOnly(true);
        return textField;
    }
}
