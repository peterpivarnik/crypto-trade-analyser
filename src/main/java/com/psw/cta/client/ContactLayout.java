package com.psw.cta.client;

import com.psw.cta.mail.CryptoMailSender;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Label;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.validator.EmailValidator;
import com.vaadin.flow.dom.Style;
import com.vaadin.flow.spring.annotation.SpringComponent;
import org.apache.commons.lang3.StringUtils;

import static com.psw.cta.client.ClientUtils.createButton;
import static com.psw.cta.client.ClientUtils.createTextField;

@SpringComponent
public class ContactLayout {

    private CryptoMailSender cryptoMailSender;

    public ContactLayout(CryptoMailSender cryptoMailSender) {
        this.cryptoMailSender = cryptoMailSender;
    }

    VerticalLayout getLayout() {
        Label contactLabel = prepareContactLabel();
        TextField contactMail = prepareContactMail();
        TextArea contactQuestion = prepareContactQuestion();
        Button submitButton = prepareSubmitButton(contactMail, contactQuestion);
        return new VerticalLayout(contactLabel, contactMail, contactQuestion, submitButton);
    }

    private Label prepareContactLabel() {
        Label contactLabel = ClientUtils.getLabel("If you have any question you can contact us here: ");
        contactLabel.setWidth("400px");
        Style style = contactLabel.getStyle();
        style.set("font-weight", "bold");
        return contactLabel;
    }

    private TextField prepareContactMail() {
        TextField contactMail = createTextField("Your email adress: ");
        contactMail.setHeight("150px");
        Binder<TextField> binder = new Binder<>();
        binder.forField(contactMail)
                .withValidator(new EmailValidator("Not valid email"))
                .bind(TextField::getValue, TextField::setValue);
        return contactMail;
    }

    private TextArea prepareContactQuestion() {
        TextArea contactQuestion = new TextArea("Your question: ");
        contactQuestion.setSizeFull();
        return contactQuestion;
    }

    private Button prepareSubmitButton(TextField contactMail, TextArea contactQuestion) {
        Button submitButton = createButton("Submit");
        submitButton.addClickListener(event -> onEvent(contactMail, contactQuestion));
        return submitButton;
    }

    private void onEvent(TextField contactMail, TextArea contactQuestion) {
        if (StringUtils.isEmpty(contactMail.getValue())) {
            showNotification("Please enter your email.");
            return;
        }
        if (StringUtils.isEmpty(contactQuestion.getValue())) {
            showNotification("Please enter your question.");
            return;
        }
        cryptoMailSender.send(contactMail.getValue(), contactQuestion.getValue());
        showNotification("Your question had been send.");
        contactMail.setValue("");
        contactQuestion.setValue("");
    }

    private void showNotification(String s) {
        Notification notification = new Notification(s, 3000, Notification.Position.MIDDLE);
        notification.open();
    }
}
