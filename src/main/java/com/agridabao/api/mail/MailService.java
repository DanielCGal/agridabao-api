package com.agridabao.api.mail;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Service
public class MailService {
    private static final Logger log = LoggerFactory.getLogger(MailService.class);
    private static final String HEADER_IMAGE = "email/header.png";

    private final JavaMailSender mailSender;
    private final String from;
    private final String fromName;
    private final String smtpUsername;
    private final String smtpPassword;
    private final boolean exposeCode;

    public MailService(JavaMailSender mailSender,
                       @Value("${app.mail.from:}") String from,
                       @Value("${app.mail.from-name:Team GreenScape}") String fromName,
                       @Value("${spring.mail.username:}") String smtpUsername,
                       @Value("${spring.mail.password:}") String smtpPassword,
                       @Value("${app.verification.expose-code:false}") boolean exposeCode) {
        this.mailSender = mailSender;
        this.from = from;
        this.fromName = fromName;
        this.smtpUsername = smtpUsername;
        this.smtpPassword = smtpPassword;
        this.exposeCode = exposeCode;

        log.info("Mail transport configured: {} (username={})",
                isConfigured(), smtpUsername.isBlank() ? "<empty>" : smtpUsername);
    }

    public void sendSignupCode(String email, String code) {
        send(email, "Your GreenScape Sign-up Code", "email/signup-code.html", code, "sign-up");
    }

    public void sendLoginCode(String email, String code) {
        send(email, "Your GreenScape Login Code", "email/login-code.html", code, "login");
    }

    private void send(String to, String subject, String templatePath, String code, String label) {
        if (exposeCode) {
            log.info("[DEV] {} verification code for {}: {}", label, to, code);
        }

        if (!isConfigured()) {
            if (!exposeCode) {
                throw new IllegalStateException(
                        "Email transport is not configured. Set MAIL_USERNAME and MAIL_PASSWORD.");
            }
            log.warn("MAIL_USERNAME and/or MAIL_PASSWORD are not both set; skipping real send to {} (dev mode).", to);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            // RELATED (not MIXED) since the header image is an inline resource, not a
            // real attachment; MIXED would nest it in a way some clients list as one.
            MimeMessageHelper helper = new MimeMessageHelper(
                    message, MimeMessageHelper.MULTIPART_MODE_RELATED, StandardCharsets.UTF_8.name());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setFrom(from, fromName);

            String html = loadTemplate(templatePath).replace("{{code}}", code);
            helper.setText(html, true);

            ClassPathResource header = new ClassPathResource(HEADER_IMAGE);
            if (header.exists()) {
                helper.addInline("header", header);
            } else {
                log.warn("Header image not found at classpath:{}; sending email without it.", HEADER_IMAGE);
            }

            mailSender.send(message);
            log.info("Sent {} email to {}.", label, to);
        } catch (Exception ex) {
            if (exposeCode) {
                log.warn("Failed to send {} email to {}; the code is available in the logs above.",
                        label, to, ex);
                return;
            }
            throw new IllegalStateException("Failed to send the verification email.", ex);
        }
    }

    private boolean isConfigured() {
        return smtpUsername != null && !smtpUsername.isBlank()
                && smtpPassword != null && !smtpPassword.isBlank();
    }

    private String loadTemplate(String path) throws Exception {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return StreamUtils.copyToString(in, StandardCharsets.UTF_8);
        }
    }
}
