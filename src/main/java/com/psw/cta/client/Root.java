package com.psw.cta.client;

import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.Theme;
import com.vaadin.flow.theme.lumo.Lumo;

@Route(value = "")
@Theme(Lumo.class)
@PageTitle("Crypto Trade Analyser")
public class Root extends VerticalLayout {

    public Root(MainView mainView) {
        this.add(mainView.getMainLayout());
    }

}