package com.psw.cta.client;

import com.psw.cta.client.dto.Faq;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Label;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.spring.annotation.SpringComponent;

import java.util.ArrayList;
import java.util.List;

import static com.psw.cta.client.ClientUtils.getLayoutLabel;
import static com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment.CENTER;

@SpringComponent
class FaqLayout {

    VerticalLayout getLayout() {
        Component layoutLabel = getLayoutLabel("FAQ", "100%");
        Grid<Faq> faqGrid = getFaqGrid(createFaqs());
        VerticalLayout verticalLayout = new VerticalLayout(layoutLabel, faqGrid);
        verticalLayout.setDefaultHorizontalComponentAlignment(CENTER);
        return verticalLayout;
    }

    private List<Faq> createFaqs() {
        ArrayList<Faq> faqs = new ArrayList<>();
        faqs.add(new Faq("What is source of the analysis data for Crypto Trade Analyser (CTA)? ",
                         "Data are taken from the web page <a target=\"_blank\" rel=\"noopener noreferrer\" href=\"http://www.binance.com\">www.binance.com</a> "));
        faqs.add(new Faq("How often are data updated? ",
                         "Data are updated every minute"));
        faqs.add(new Faq("Why I can not see updated data? ",
                         "For update of data you have to refresh the web page"));
        faqs.add(new Faq("Why there are no data on analysis page? ",
                         "This can have two reasons. Either exchange binance.com is currently down (check the webpage), or it is simply bad time for investing."));
        faqs.add(new Faq("Should I invest my money according CTA?",
                         "NO! CTA is only analysing tool with some prediction. Investing money according CTA analysis is not recommended!"));
        faqs.add(new Faq("What is he meaning of columns on Analysis?",
                         "<u>Life time:</u> Time how long is curent alalysis old. <br><u>Symbol:</u> Pair of cryptocurrencies that are analysed <br><u>Current price:</u> Current price of crytpo trade <br><u>Price to sell:</u> Recommended price for sell <br><u>Percent:</u> Calculated profit in percentage if currency was bought for Current price and will be sold for Price to sell"));
        faqs.add(new Faq("After what time price will be at Price for sell? ",
                         "Usually in 24 hours"));
        faqs.add(new Faq("How are statistics calculated?",
                         "Statistics are calculating for last 24 hours. After 24 hours data for last day, last week and last month are displayed."));
        faqs.add(new Faq("What means numbers on Statistics page?",
                         "There is percentual success of all analysed data. For example Last day statistic 80 means that 80 analysed data out of 100 were successful."));
        return faqs;
    }

    private Grid<Faq> getFaqGrid(List<Faq> faqs) {
        Grid<Faq> grid = new Grid<>();
        grid.setItems(faqs);
        grid.addColumn(Faq::getQuestion).setTextAlign(ColumnTextAlign.END);
        grid.addColumn(new ComponentRenderer<>(faq -> {
            Label label = ClientUtils.createLabel();
            label.getElement().setProperty("innerHTML", faq.getAnswer());
            return label;
        }));
        grid.addThemeVariants(GridVariant.LUMO_NO_BORDER,
                              GridVariant.LUMO_NO_ROW_BORDERS,
                              GridVariant.LUMO_WRAP_CELL_CONTENT);
        grid.setHeightByRows(true);
        return grid;
    }
}
