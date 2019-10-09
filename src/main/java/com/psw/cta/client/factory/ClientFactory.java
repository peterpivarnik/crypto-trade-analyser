package com.psw.cta.client.factory;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Label;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.dom.Style;
import com.vaadin.flow.spring.annotation.SpringComponent;

import static com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment.CENTER;

@SpringComponent
public class ClientFactory {

    private Label createLabel(String text) {
        Label label = createLabel();
        label.setText(text);
        return label;
    }

    private Label createLabel() {
        return new Label();
    }

    public TextField createTextField(String label, String value) {
        TextField textField = createTextField(label);
        textField.setValue(value);
        textField.setReadOnly(true);
        return textField;
    }

    private TextField createTextField(String label) {
        TextField textField = new TextField();
        textField.setLabel(label);
        return textField;
    }

    public Label createLayoutLabel(String labelName, String labelSize) {
        Label mainLabel = createLabel(labelName);
        Style style = mainLabel.getStyle();
        style.set("font-size", labelSize);
        style.set("font-weight", "bold");
        style.set("color", "var(--lumo-primary-text-color)");
        return mainLabel;
    }

    public VerticalLayout createVerticalLayout(Component... children) {
        VerticalLayout verticalLayout = new VerticalLayout(children);
        verticalLayout.setDefaultHorizontalComponentAlignment(CENTER);
        return verticalLayout;
    }

}
