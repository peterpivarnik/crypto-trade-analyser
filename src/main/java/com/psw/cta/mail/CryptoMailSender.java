package com.psw.cta.mail;

import org.springframework.stereotype.Component;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.time.Instant;
import java.util.Properties;

@Component
public class CryptoMailSender {

    private CryptoMailProperties cryptoMailProperties;

    public CryptoMailSender(CryptoMailProperties cryptoMailProperties) {
        this.cryptoMailProperties = cryptoMailProperties;
    }

    public void send(String mail, String question) {
        String to = cryptoMailProperties.getMailAddress();
        String subject = "CTA Question nr: " + Instant.now().toEpochMilli();
        final String from = cryptoMailProperties.getMailAddress();
        final String password = cryptoMailProperties.getMailPassword();

        Properties properties = prepareProperties();
        Session session = prepareSession(from, password, properties);

        try {
            Transport transport = session.getTransport();
            InternetAddress addressFrom = new InternetAddress(from);
            MimeMessage message = prepareMimeMessage(mail, question, to, subject, session, addressFrom);
            transport.connect();
            Transport.send(message);
            transport.close();
        } catch (MessagingException e) {
            throw new RuntimeException("Problem during sending mail", e);
        }
    }

    private Properties prepareProperties() {
        Properties props = new Properties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.host", "smtp.gmail.com");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.port", "465");
        props.put("mail.debug", "true");
        props.put("mail.smtp.socketFactory.port", "465");
        props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
        props.put("mail.smtp.socketFactory.fallback", "false");
        return props;
    }

    private Session prepareSession(String from, String password, Properties properties) {
        return Session.getDefaultInstance(
                properties,
                new javax.mail.Authenticator() {
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(from, password);
                    }
                });
    }

    private MimeMessage prepareMimeMessage(String mail,
                                           String question,
                                           String to,
                                           String subject,
                                           Session session,
                                           InternetAddress addressFrom) throws MessagingException {
        MimeMessage message = new MimeMessage(session);
        message.setSender(addressFrom);
        message.setSubject(subject);
        message.setContent(question, "text/plain");
        message.addRecipient(Message.RecipientType.TO, new InternetAddress(to));
        message.addRecipient(Message.RecipientType.TO, new InternetAddress(mail));
        return message;
    }
}
