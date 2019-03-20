package com.psw.cta.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("server.mail")
public class CryptoMailProperties {

    private String mailAddress;

    private String mailPassword;

    public String getMailAddress() {
        return mailAddress;
    }

    public void setMailAddress(String mailAddress) {
        this.mailAddress = mailAddress;
    }

    public String getMailPassword() {
        return mailPassword;
    }

    public void setMailPassword(String mailPassword) {
        this.mailPassword = mailPassword;
    }
}
