package com.psw.cta.client;

import com.psw.cta.mail.CryptoMailSender;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Label;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.dom.Style;
import com.vaadin.flow.spring.annotation.SpringComponent;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

import javax.mail.MessagingException;

import static com.psw.cta.client.ClientUtils.createButton;
import static com.psw.cta.client.ClientUtils.createTextField;

@SpringComponent
public class ContactLayout {

    @Autowired
    private CryptoMailSender cryptoMailSender;

    public VerticalLayout getLayout() {
        Label contactLabel = ClientUtils.getLabel("If you have any question you can contact us here: ");
        contactLabel.setWidth("400px");
        Style style = contactLabel.getStyle();
        style.set("font-weight", "bold");
        TextField contactMail = createTextField("Your email adress: ");
        contactMail.setHeight("150px");
        TextArea contactQuestion = new TextArea("Your question: ");
        contactQuestion.setSizeFull();
        Button submitButton = createButton("Submit");
        submitButton.addClickListener(event -> onEvent(event, contactMail, contactQuestion));
        return new VerticalLayout(contactLabel, contactMail, contactQuestion, submitButton);
    }

    private void onEvent(ClickEvent<Button> event,
                         TextField contactMail,
                         TextArea contactQuestion) {
        if (StringUtils.isEmpty(contactMail.getValue())) {
            showNotification("Please enter your email.");
            return;
        }
        if (StringUtils.isEmpty(contactQuestion.getValue())) {
            showNotification("Please enter your question.");
            return;
        }
        try {
            cryptoMailSender.send();
        } catch (MessagingException e) {
            throw new RuntimeException("Exception during sending mail", e);
        }
        showNotification("Your question had been send.");
        contactMail.setValue("");
        contactQuestion.setValue("");
    }

    private void showNotification(String s) {
        Notification notification = new Notification(s, 3000, Notification.Position.MIDDLE);
        notification.open();
    }
}
