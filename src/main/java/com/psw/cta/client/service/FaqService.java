package com.psw.cta.client.service;

import com.psw.cta.client.dto.Faq;
import com.psw.cta.client.factory.ClientFactory;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Label;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.spring.annotation.SpringComponent;

import java.util.List;

@SpringComponent
class FaqService {

    private ClientFactory clientFactory;

    public FaqService(ClientFactory clientFactory) {
        this.clientFactory = clientFactory;
    }

    VerticalLayout getFaqLayout() {
        Component layoutLabel = clientFactory.createLayoutLabel("FAQ", "100%");
        Grid<Faq> faqGrid = getFaqGrid();
        return clientFactory.createVerticalLayout(layoutLabel, faqGrid);
    }

    private Grid<Faq> getFaqGrid() {
        List<Faq> faqs = Faq.createFaqs();
        Grid<Faq> grid = new Grid<>();
        grid.setItems(faqs);
        grid.addColumn(Faq::getQuestion).setTextAlign(ColumnTextAlign.END);
        grid.addColumn(new ComponentRenderer<>(faq -> {
            Label label = clientFactory.createLabel();
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
