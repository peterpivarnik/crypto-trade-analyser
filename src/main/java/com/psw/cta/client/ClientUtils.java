package com.psw.cta.client;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Label;
import com.vaadin.flow.component.textfield.TextField;

public class ClientUtils {

    public static Label createLabel(String text) {
        Label label = createLabel();
        label.setText(text);
        return label;
    }

    public static Label createLabel() {
        Label label = new Label();
        label.setWidth("350px");
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
}
