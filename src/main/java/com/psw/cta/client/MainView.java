package com.psw.cta.client;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.dom.Style;
import com.vaadin.flow.spring.annotation.SpringComponent;

import static com.psw.cta.client.ClientUtils.getLayoutLabel;
import static com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment.CENTER;

@SpringComponent
public class MainView {

    private AnalysisLayout analysisLayout;
    private StatisticLayout statisticLayout;
    private FaqLayout faqLayout;
    private ContactLayout contactLayout;

    public MainView(AnalysisLayout analysisLayout,
                    StatisticLayout statisticLayout,
                    FaqLayout faqLayout,
                    ContactLayout contactLayout) {
        this.analysisLayout = analysisLayout;
        this.statisticLayout = statisticLayout;
        this.faqLayout = faqLayout;
        this.contactLayout = contactLayout;
    }

    VerticalLayout getMainLayout() {
        VerticalLayout contentLayout = new VerticalLayout();
        VerticalLayout analysis = analysisLayout.getLayout();
        VerticalLayout statistics = statisticLayout.getLayout();
        VerticalLayout faq = faqLayout.getLayout();
        VerticalLayout contact = contactLayout.getLayout();
        Component mainLabel = getLayoutLabel("Crypto Trade Analyser", "200%");
        contentLayout.add(mainLabel,
                          analysis,
                          statistics,
                          faq,
                          contact);
        contentLayout.setDefaultHorizontalComponentAlignment(CENTER);
        return contentLayout;
    }
}