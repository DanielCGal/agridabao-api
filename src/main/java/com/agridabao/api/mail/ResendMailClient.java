package com.agridabao.api.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Sends mail through Resend's HTTPS API instead of SMTP.
 *
 * Cloud hosts (Railway, Render, Fly) block outbound SMTP ports to prevent spam,
 * so JavaMailSender times out there. This client talks to port 443, which is
 * never blocked, and is selected automatically whenever an API key is present.
 */
@Component
public class ResendMailClient {
    private static final Logger log = LoggerFactory.getLogger(ResendMailClient.class);
    private static final String ENDPOINT = "https://api.resend.com/emails";

    private final String apiKey;
    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    public ResendMailClient(@Value("${app.mail.resend.api-key:}") String apiKey) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public boolean isEnabled() {
        return !apiKey.isBlank();
    }

    /**
     * @param inlineImage optional base64 payload for the template's cid:header
     *                    image; pass null to send without it.
     */
    public void send(String fromAddress,
                     String fromName,
                     String to,
                     String subject,
                     String html,
                     String inlineImageBase64,
                     String inlineImageFilename,
                     String inlineImageContentId) {

        ObjectNode body = mapper.createObjectNode();
        body.put("from", buildFrom(fromAddress, fromName));
        ArrayNode recipients = body.putArray("to");
        recipients.add(to);
        body.put("subject", subject);
        body.put("html", html);

        if (inlineImageBase64 != null && !inlineImageBase64.isBlank()) {
            ArrayNode attachments = body.putArray("attachments");
            ObjectNode image = attachments.addObject();
            image.put("filename", inlineImageFilename);
            image.put("content", inlineImageBase64);
            // Matches src="cid:header" in the HTML templates.
            image.put("content_id", inlineImageContentId);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ENDPOINT))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        mapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> response =
                    http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(
                        "Resend rejected the email (HTTP " + response.statusCode() + "): " + response.body());
            }

            log.info("Sent email to {} via Resend.", to);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while sending the email.", ex);
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to reach the Resend API.", ex);
        }
    }

    private static String buildFrom(String address, String name) {
        if (name == null || name.isBlank())
            return address;
        return name + " <" + address + ">";
    }
}
