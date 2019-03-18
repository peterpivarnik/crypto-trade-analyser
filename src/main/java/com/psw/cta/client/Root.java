package com.psw.cta.client;

import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.page.Inline;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.Theme;
import com.vaadin.flow.theme.lumo.Lumo;

import static com.vaadin.flow.component.page.Inline.Position.PREPEND;
import static com.vaadin.flow.component.page.Inline.Wrapping.JAVASCRIPT;
import static com.vaadin.flow.component.page.TargetElement.HEAD;

@Route(value = "")
@Theme(Lumo.class)
@PageTitle("Crypto Trade Analyser")
@Inline(value = "analytics.js", target = HEAD, position = PREPEND, wrapping = JAVASCRIPT)
public class Root extends VerticalLayout {

    public Root(MainView mainView) {
        this.add(mainView.getMainLayout());
    }

}