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
                                        "Data are taken from the web page <a target=\"_blank\" rel=\"noopener noreferrer\" href=\"http://www.binance.com\">www.binance.com</a> ");
        VerticalLayout faq2 = createFaq("How often are data updated? ",
                                        "Data are updated every minute");
        VerticalLayout faq3 = createFaq("Why I can not see updated data? ",
                                        "For update of data you have to refresh the web page");
        VerticalLayout faq4 = createFaq("Why there are no data on analysis page? ",
                                        "This can have two reasons. Either exchange binance.com is currently down (check the webpage), or it is simply bad time for investing.");
        VerticalLayout faq5 = createFaq("Should I invest my money according CTA?",
                                        "NO! CTA is only analysing tool with some prediction. Investing money according CTA analysis is not recommended!");
        VerticalLayout faq6 = createFaq("What is he meaning of columns on Analysis?",
                                        "<u>Life time:</u> Time how long is curent alalysis old. <br><u>Symbol:</u> Pair of cryptocurrencies that are analysed <br><u>Current price:</u> Current price of crytpo trade <br><u>Price to sell:</u> Recommended price for sell <br><u>Percent:</u> Calculated profit in percentage if currency was bought for Current price and will be sold for Price to sell");
        VerticalLayout faq7 = createFaq("After what time price will be at Price for sell? ",
                                        "Usually in 24 hours");
        VerticalLayout faq8 = createFaq("How are statistics calculated?",
                                        "Statistics are calculating for last 24 hours. After 24 hours data for last day, last week and last month are displayed.");
        VerticalLayout faq9 = createFaq("What means numbers on Statistics page?",
                                        "There is percentual success of all analysed data. For example Last day statistic 80 means that 80 analysed data out of 100 were successful.");
        return new VerticalLayout(faq1, faq2, faq3, faq4, faq5, faq6, faq7, faq8, faq9);
    }

    private VerticalLayout createFaq(String questionText, String answerText) {
        HorizontalLayout question = createQuestionLayout(questionText);
        Style style = question.getStyle();
        style.set("background-color", "var(--lumo-contrast-5pct)");
        HorizontalLayout asnwer = createAnswerLayout(answerText);
        return new VerticalLayout(question, asnwer);
    }

    private HorizontalLayout createQuestionLayout(String questionText) {
        Label qLabel = ClientUtils.createLabel("Q: ");
        qLabel.setWidth("20px");
        Style qLabelStyle = qLabel.getStyle();
        qLabelStyle.set("font-weight", "bold");
        qLabelStyle.set("font-size", "large");

        Label questionLabel = ClientUtils.createLabel(questionText);
        questionLabel.setWidth("1000px");
        Style questionLabelStyle = questionLabel.getStyle();
        questionLabelStyle.set("font-weight", "bold");

        return new HorizontalLayout(qLabel, questionLabel);
    }

    private HorizontalLayout createAnswerLayout(String answerText) {
        Label aLabel = ClientUtils.createLabel("A: ");
        aLabel.setWidth("20px");
        Label answerLabel = ClientUtils.createLabel();
        answerLabel.setWidth("1000px");
        answerLabel.getElement().setProperty("innerHTML", answerText);
        return new HorizontalLayout(aLabel, answerLabel);
    }
}
