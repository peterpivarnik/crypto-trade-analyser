package com.psw.cta.client.dto;

public class Faq {

    private String question;
    private String answer;

    public Faq(String question, String answerText) {
        this.question = question;
        this.answer = answerText;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }
}
