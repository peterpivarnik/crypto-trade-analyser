package com.psw.cta.client.service;

import com.psw.cta.client.factory.ClientFactory;
import com.vaadin.flow.component.html.Label;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.dom.Style;
import com.vaadin.flow.spring.annotation.SpringComponent;

@SpringComponent
public class MainViewService {

    private AnalysisService analysisService;
    private StatisticsService statisticsService;
    private FaqService faqService;
    private ContactService contactService;
    private ClientFactory clientFactory;

    public MainViewService(AnalysisService analysisService,
                           StatisticsService statisticsService,
                           FaqService faqService,
                           ContactService contactService,
                           ClientFactory clientFactory) {
        this.analysisService = analysisService;
        this.statisticsService = statisticsService;
        this.faqService = faqService;
        this.contactService = contactService;
        this.clientFactory = clientFactory;
    }

    public VerticalLayout getMainViewLayout() {
        Label layoutLabel = clientFactory.createLayoutLabel("Crypto Trade Analyser", "200%");
        VerticalLayout analysisLayout = analysisService.getAnalysisLayout();
        VerticalLayout statisticsLayout = statisticsService.getStatisticLayout();
        setBackgroundColor(statisticsLayout.getStyle());
        VerticalLayout faqLayout = faqService.getFaqLayout();
        VerticalLayout contactLayout = contactService.getContactLayout();
        setBackgroundColor(contactLayout.getStyle());
        return clientFactory.createVerticalLayout(layoutLabel,
                                                  analysisLayout,
                                                  statisticsLayout,
                                                  faqLayout,
                                                  contactLayout);
    }

    private void setBackgroundColor(Style style) {
        style.set("background-color", "var(--lumo-contrast-10pct)");
    }
}