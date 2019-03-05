package com.psw.cta.client;

import com.vaadin.flow.component.html.Label;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.dom.Style;
import com.vaadin.flow.spring.annotation.SpringComponent;

@SpringComponent
public class FaqLayout {

    public HorizontalLayout getLayout() {
        return new HorizontalLayout(createFaqLayout());
    }

    private VerticalLayout createFaqLayout() {
        VerticalLayout faq1 = createFaq("How does it works? ", "It works very well. ");
        VerticalLayout faq2 = createFaq(
                "jiaschd iuwdh oiuqwhd wouidhqowudh wohoqwhdo qwhdo qowdh oqoqwhd oqwihd oqwidh qowidh qowid qowidh oqwidh oqwd oiqwhd ? ",
                "ohwqfufh ioeufh oiuhwef owehfo hefoihweo fiwheofi hoeifh oweifoiwef owiejf oiwefoi weoif oweifo iwefoi oiwef oweif owefoij ofij owjowiejf owejfoi wefoiwe ofwef oweifj oweifjo ewiwoeifj owiejfoqiwjf pwijf oiwjf pweijf pweijfpi wejpijwf wefij ");
        return new VerticalLayout(faq1, faq2);
    }

    private VerticalLayout createFaq(String questionText, String answerText) {
        HorizontalLayout question = createQuestionLayout(questionText);
        Style style = question.getStyle();
        style.set("background-color", "var(--lumo-contrast-5pct)");
        HorizontalLayout asnwer = createAnswerLayout(answerText);
        return new VerticalLayout(question, asnwer);
    }

    private HorizontalLayout createQuestionLayout(String questionText) {
        Label qLabel = ClientUtils.getLabel("Q: ");
        qLabel.setWidth("20px");
        Style qLabelStyle = qLabel.getStyle();
        qLabelStyle.set("font-weight", "bold");
        qLabelStyle.set("font-size", "large");

        Label questionLabel = ClientUtils.getLabel(questionText);
        questionLabel.setWidth("1000px");
        Style questionLabelStyle = questionLabel.getStyle();
        questionLabelStyle.set("font-weight", "bold");

        return new HorizontalLayout(qLabel, questionLabel);
    }

    private HorizontalLayout createAnswerLayout(String answerText) {
        Label aLabel = ClientUtils.getLabel("A: ");
        aLabel.setWidth("20px");
        Label answerLabel = ClientUtils.getLabel(answerText);
        answerLabel.setWidth("1000px");
        return new HorizontalLayout(aLabel, answerLabel);
    }
}
