package com.psw.cta.client;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Label;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.dom.Style;

public class ClientUtils {

    public static Label createLabel(String text) {
        Label label = createLabel();
        label.setText(text);
        return label;
    }

    public static Label createLabel() {
        Label label = new Label();
//        label.setWidth("350px");
        return label;
    }

    public static TextField createTextField(String label, String value) {
        TextField textField = createTextField(label);
        textField.setValue(value);
        return textField;
    }
    public static TextField createTextField(String label) {
        TextField textField = new TextField();
        textField.setLabel(label);
        return textField;
    }

    public static Button createButton(String label) {
        Button button = new Button();
        button.setText(label);
        return button;
    }

    public static Label getLayoutLabel(String labelName, String labelSize) {
        Label mainLabel = createLabel(labelName);
        Style style = mainLabel.getStyle();
        style.set("font-size", labelSize);
        style.set("font-weight", "bold");
        style.set("color", "var(--lumo-primary-text-color)");
        return mainLabel;
    }
}
