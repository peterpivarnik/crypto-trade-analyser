package com.psw.cta.client;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;

@Route("greeting")
public class HelloWorld extends VerticalLayout {
    public HelloWorld() {
        TextField textField = new TextField("Name");

        Button button = new Button("Greet");
        button.addClickListener(event -> add(new Span("Hello, " + textField.getValue())));

        add(textField, button);
    }
}