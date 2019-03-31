package com.psw.cta.client;

import com.psw.cta.service.CacheService;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Label;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.dom.Style;
import com.vaadin.flow.spring.annotation.SpringComponent;

import static com.psw.cta.client.ClientUtils.createLabel;

@SpringComponent
public class MainView {

    private AnalysisLayout analysisLayout;
    private StatisticLayout statisticLayout;
    private FaqLayout faqLayout;
    private ContactLayout contactLayout;

    public MainView(CacheService cacheService,
                    AnalysisLayout analysisLayout,
                    StatisticLayout statisticLayout,
                    FaqLayout faqLayout,
                    ContactLayout contactLayout) {
        this.analysisLayout = analysisLayout;
        this.statisticLayout = statisticLayout;
        this.faqLayout = faqLayout;
        this.contactLayout = contactLayout;
    }

    public VerticalLayout getMainLayout() {
        HorizontalLayout mainLabelLayout = getMainLabelLayout();
        HorizontalLayout contentLayout = new HorizontalLayout();
        addAnalysisLayout(contentLayout);
        HorizontalLayout buttonLayout = getButtonLayout(contentLayout);
        VerticalLayout mainLayout = new VerticalLayout(mainLabelLayout, buttonLayout, contentLayout);
        mainLayout.setHorizontalComponentAlignment(Alignment.CENTER, mainLabelLayout, buttonLayout, contentLayout);
        return mainLayout;
    }

    private HorizontalLayout getMainLabelLayout() {
        Label mainLabel = createLabel("Crypto Trade Analyser");
        Style style = mainLabel.getStyle();
        style.set("font-size", "200%");
        style.set("font-weight", "bold");
        style.set("color", "var(--lumo-primary-text-color)");
        return new HorizontalLayout(mainLabel);
    }

    private HorizontalLayout getButtonLayout(HorizontalLayout contentLayout) {
        Button analysisButton = new Button("Analysis",
                                           (ComponentEventListener<ClickEvent<Button>>) buttonClickEvent -> addAnalysisLayout(
                                                   contentLayout));
        Button statisticButton = new Button("Statistics",
                                            (ComponentEventListener<ClickEvent<Button>>) buttonClickEvent -> addStatisticLayout(
                                                    contentLayout));
        Button faqButton = new Button("FAQ",
                                      (ComponentEventListener<ClickEvent<Button>>) buttonClickEvent -> addFaqLayout(
                                              contentLayout));
        Button contactButton = new Button("Contact",
                                          (ComponentEventListener<ClickEvent<Button>>) buttonClickEvent -> addContactLayout(
                                                  contentLayout));
        return new HorizontalLayout(analysisButton, statisticButton, faqButton, contactButton);
    }

    private void addAnalysisLayout(HorizontalLayout contentLayout) {
        contentLayout.removeAll();
        contentLayout.add(analysisLayout.getLayout());
    }

    private void addStatisticLayout(HorizontalLayout contentLayout) {
        contentLayout.removeAll();
        contentLayout.add(statisticLayout.getLayout());
    }

    private void addFaqLayout(HorizontalLayout contentLayout) {
        contentLayout.removeAll();
        contentLayout.add(faqLayout.getLayout());
    }

    private void addContactLayout(HorizontalLayout contentLayout) {
        contentLayout.removeAll();
        contentLayout.add(contactLayout.getLayout());
    }

}