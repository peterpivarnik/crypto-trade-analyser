package com.psw.cta.client.service;

import com.psw.cta.client.factory.ClientFactory;
import com.psw.cta.rest.dto.SuccessRate;
import com.psw.cta.service.CacheService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.spring.annotation.SpringComponent;

@SpringComponent
public class SuccessService {

    private CacheService cacheService;
    private ClientFactory clientFactory;

    public SuccessService(CacheService cacheService, ClientFactory clientFactory) {
        this.cacheService = cacheService;
        this.clientFactory = clientFactory;
    }

    VerticalLayout getSuccessRateLayout() {
        Component layoutLabel = clientFactory.createLayoutLabel("Success rate", "100%");
        VerticalLayout successRateFieldsLayout = getSuccessRateFieldsLayout(cacheService.getSuccessRate());
        return clientFactory.createVerticalLayout(layoutLabel, successRateFieldsLayout);
    }

    private VerticalLayout getSuccessRateFieldsLayout(SuccessRate successRate) {
        TextField lastMonthSuccessTextField = clientFactory.createTextField("One day success",
                                                                          String.valueOf(successRate.getOneDaySuccessRate()));
        TextField lastWeekSuccessTextField = clientFactory.createTextField("Two day success",
                                                                         String.valueOf(successRate.getTwoDaysSuccessRate()));
        TextField lastDaySuccessTextField = clientFactory.createTextField("One week success",
                                                                        String.valueOf(successRate.getOneWeekSuccessRate()));
        return clientFactory.createVerticalLayout(lastMonthSuccessTextField,
                                                  lastWeekSuccessTextField,
                                                  lastDaySuccessTextField);
    }
}
