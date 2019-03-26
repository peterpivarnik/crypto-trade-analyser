package com.psw.cta.client;

import com.vaadin.flow.component.html.Label;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.dom.Style;
import com.vaadin.flow.spring.annotation.SpringComponent;

@SpringComponent
class FaqLayout {

    HorizontalLayout getLayout() {
        return new HorizontalLayout(createFaqLayout());
    }

    private VerticalLayout createFaqLayout() {
        VerticalLayout faq1 = createFaq("What is source of the analysis data for Crypto Trade Analyser (CTA)? ",
                                        "Data are taken from the web page www.binance.com ");
        VerticalLayout faq2 = createFaq("How often are data updated? ",
                                        "Data are updated every minute");
        VerticalLayout faq3 = createFaq("After what time price will be at Price for sell? ",
                                        "Usually in 24 hours");
        VerticalLayout faq4 = createFaq("Why I can not see updated data? ",
                                        "For update of data you have to refresh the web page");
        VerticalLayout faq5 = createFaq("Why there are no data on analysis page? ",
                                        "This can have two reasons. Either exchange binance.com is currently down (check the webpage), or it is simply bad time for investing.");
        VerticalLayout faq6 = createFaq("Should I invest my money according CTA?",
                                        "NO! CTA is only analysing tool with some prediction. Investing money according CTA analysis is not recommended!");
        VerticalLayout faq7 = createFaq("How are statistics calculated?",
                                        "Statistics are calculating for last 24 hours. After 24 hours data for last day, last week and last month are displayed.");
        return new VerticalLayout(faq1, faq2, faq3, faq4, faq5, faq6, faq7);
    }

    private VerticalLayout createFaq(String questionText, String answerText) {
        HorizontalLayout question = createQuestionLayout(questionText);
        Style style = question.getStyle();
        style.set("background-color", "var(--lumo-contrast-5pct)");
        HorizontalLayout asnwer = createAnswerLayout(answerText);
        return new VerticalLayout(question, asnwer);
    }

    private HorizontalLayout createQuestionLayout(String questionText) {
        Label qLabel = ClientUtils.getLabel("Q: ");
        qLabel.setWidth("20px");
        Style qLabelStyle = qLabel.getStyle();
        qLabelStyle.set("font-weight", "bold");
        qLabelStyle.set("font-size", "large");

        Label questionLabel = ClientUtils.getLabel(questionText);
        questionLabel.setWidth("1000px");
        Style questionLabelStyle = questionLabel.getStyle();
        questionLabelStyle.set("font-weight", "bold");

        return new HorizontalLayout(qLabel, questionLabel);
    }

    private HorizontalLayout createAnswerLayout(String answerText) {
        Label aLabel = ClientUtils.getLabel("A: ");
        aLabel.setWidth("20px");
        Label answerLabel = ClientUtils.getLabel(answerText);
        answerLabel.setWidth("1000px");
        return new HorizontalLayout(aLabel, answerLabel);
    }
}
