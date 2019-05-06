package com.psw.cta.client;

import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinServiceInitListener;
import com.vaadin.flow.spring.annotation.SpringComponent;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;

@SpringComponent
public class ApplicationServiceInitListener implements VaadinServiceInitListener {

    @Override
    public void serviceInit(ServiceInitEvent event) {
        event.addBootstrapListener(response -> {
            Document document = response.getDocument();
            Element head = document.head();
            head.prependChild(createScript(
                    document,
                    "(adsbygoogle = window.adsbygoogle || []).push({google_ad_client: \"ca-pub-4581860897391212\", enable_page_level_ads: true});"));
            head.prependChild(createAsyncScript(
                    document, "//pagead2.googlesyndication.com/pagead/js/adsbygoogle.js"));
            head.prependChild(createScript(
                    document,
                    "window.dataLayer = window.dataLayer || []; function gtag(){dataLayer.push(arguments);} gtag('js', new Date()); gtag('config', 'UA-135919592-2');"));
            head.prependChild(createAsyncScript(
                    document,
                    "https://www.googletagmanager.com/gtag/js?id=UA-135919592-2"));
        });

        event.addDependencyFilter((dependencies, filterContext) -> dependencies);
        event.addRequestHandler((session, request, response) -> false);
    }

    private Element createAsyncScript(Document document, String attributeValue) {
        Element meta = document.createElement("script");
        meta.attr("async", null);
        meta.attr("src", attributeValue);
        return meta;
    }

    private Node createScript(Document document, String value) {
        Element meta = document.createElement("script");
        meta.text(value);
        return meta;
    }
}
