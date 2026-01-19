package com.finalProject.BookingMeetingRoom.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

@Configuration
public class MailConfig {

    @Value("${spring.mail.host}")
    public String host;

    @Value("${spring.mail.port}")
    public int port;

    @Value("${spring.mail.properties.mail.smtp.auth}")
    public String auth;

    @Value("${spring.mail.properties.mail.smtp.starttls.enable}")
    public String starttls;

    @Value("${spring.mail.properties.mail.smtp.connectiontimeout}")
    public String connection_timeout;

    @Value("${spring.mail.properties.mail.smtp.timeout}")
    public String timeout;

    @Value("${spring.mail.properties.mail.smtp.writetimeout}")
    public String write_timeout;

    @Bean
    public JavaMailSender javaMailSender() {

        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(host);
        mailSender.setPort(port);

        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.smtp.auth", auth);
        props.put("mail.smtp.starttls.enable", starttls);
        props.put("mail.smtp.connectiontimeout", connection_timeout);
        props.put("mail.smtp.timeout", timeout);
        props.put("mail.smtp.writetimeout", write_timeout);

        return mailSender;
    }
}