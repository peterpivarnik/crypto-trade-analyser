package com.psw.cta.client;

import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.Theme;
import com.vaadin.flow.theme.lumo.Lumo;
import com.psw.cta.client.tracking.EnableGoogleAnalytics;
import com.psw.cta.client.tracking.GoogleAnalyticsTracker;
import com.psw.cta.client.tracking.TrackerConfiguration;
import com.psw.cta.client.tracking.TrackerConfigurator;

@Route(value = "")
@Theme(Lumo.class)
@EnableGoogleAnalytics(value = "UA-135919592-2")
@PageTitle("Crypto Trade Analyser")
public class Root extends VerticalLayout implements TrackerConfigurator {

    public Root(MainView mainView) {
        GoogleAnalyticsTracker.getCurrent().sendEvent("Examples",
                                                      "Event button");
        this.add(mainView.getMainLayout());
    }

    @Override
    public void configureTracker(TrackerConfiguration configuration) {
        configuration.setCreateField("allowAnchor", Boolean.FALSE);
        configuration.setInitialValue("transport", "beacon");
    }
}