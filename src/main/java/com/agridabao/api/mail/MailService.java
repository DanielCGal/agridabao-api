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
import java.util.Base64;

@Service
public class MailService {
    private static final Logger log = LoggerFactory.getLogger(MailService.class);
    private static final String HEADER_IMAGE = "email/header.png";

    /**
     * Gmail clips messages larger than ~102 KB and big inline images make every
     * send slow, so an oversized header is dropped rather than shipped.
     */
    private static final long MAX_INLINE_IMAGE_BYTES = 400_000L;

    private final JavaMailSender mailSender;
    private final ResendMailClient resend;
    private final String from;
    private final String fromName;
    private final String smtpUsername;
    private final String smtpPassword;
    private final boolean exposeCode;

    public MailService(JavaMailSender mailSender,
                       ResendMailClient resend,
                       @Value("${app.mail.from:}") String from,
                       @Value("${app.mail.from-name:Team GreenScape}") String fromName,
                       @Value("${spring.mail.username:}") String smtpUsername,
                       @Value("${spring.mail.password:}") String smtpPassword,
                       @Value("${app.verification.expose-code:false}") boolean exposeCode) {
        this.mailSender = mailSender;
        this.resend = resend;
        this.from = from;
        this.fromName = fromName;
        this.smtpUsername = smtpUsername;
        this.smtpPassword = smtpPassword;
        this.exposeCode = exposeCode;

        log.info("Mail transport: {} (from={})",
                resend.isEnabled() ? "Resend HTTPS API" : "SMTP",
                from.isBlank() ? "<empty>" : from);
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
                        "Email transport is not configured. Set RESEND_API_KEY, " +
                        "or MAIL_USERNAME and MAIL_PASSWORD for SMTP.");
            }
            log.warn("No mail transport configured; skipping real send to {} (dev mode).", to);
            return;
        }

        // Preferred on cloud hosts: HTTPS instead of a blocked SMTP port.
        if (resend.isEnabled()) {
            try {
                sendViaResend(to, subject, templatePath, code, label);
                return;
            } catch (Exception ex) {
                if (exposeCode) {
                    log.warn("Failed to send {} email to {} via Resend; the code is in the logs above.",
                            label, to, ex);
                    return;
                }
                throw new IllegalStateException("Failed to send the verification email.", ex);
            }
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

    private void sendViaResend(String to, String subject, String templatePath,
                               String code, String label) throws Exception {
        String html = loadTemplate(templatePath).replace("{{code}}", code);

        String imageBase64 = null;
        ClassPathResource header = new ClassPathResource(HEADER_IMAGE);
        if (header.exists()) {
            long size = header.contentLength();
            if (size <= MAX_INLINE_IMAGE_BYTES) {
                try (InputStream in = header.getInputStream()) {
                    imageBase64 = Base64.getEncoder().encodeToString(in.readAllBytes());
                }
            } else {
                log.warn("Header image is {} KB, over the {} KB inline limit; sending without it. " +
                                "Compress classpath:{} to restore the banner.",
                        size / 1024, MAX_INLINE_IMAGE_BYTES / 1024, HEADER_IMAGE);
            }
        } else {
            log.warn("Header image not found at classpath:{}; sending email without it.", HEADER_IMAGE);
        }

        resend.send(from, fromName, to, subject, html, imageBase64, "header.png", "header");
        log.info("Sent {} email to {}.", label, to);
    }

    /**
     * True when any transport can actually deliver: the Resend API key, or a
     * complete SMTP username/password pair.
     */
    private boolean isConfigured() {
        if (resend.isEnabled())
            return true;

        return smtpUsername != null && !smtpUsername.isBlank()
                && smtpPassword != null && !smtpPassword.isBlank();
    }

    private String loadTemplate(String path) throws Exception {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return StreamUtils.copyToString(in, StandardCharsets.UTF_8);
        }
    }
}
