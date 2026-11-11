package ru.anyforms.email.service.provider.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.resolver.DefaultAddressResolverGroup;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import ru.anyforms.email.exception.EmailDeliveryException;
import ru.anyforms.email.model.EmailType;
import ru.anyforms.email.service.provider.EmailProvider;
import ru.anyforms.email.service.provider.OutgoingEmail;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
class NotiSendEmailProvider implements EmailProvider {

    private static final String NOTISEND_BASE_URL = "https://api.notisend.ru/v1";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    NotiSendEmailProvider(ObjectMapper objectMapper, @Value("${email.notisend.api.key}") String apiKey) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        HttpClient http = HttpClient.create()
                .resolver(DefaultAddressResolverGroup.INSTANCE)
                .responseTimeout(TIMEOUT);
        this.webClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(http))
                .baseUrl(NOTISEND_BASE_URL)
                .build();
    }

    @Override
    public EmailType type() {
        return EmailType.MARKETING;
    }

    @Override
    public String name() {
        return "NotiSend";
    }

    @Override
    public boolean credentialsConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public String send(OutgoingEmail email) {
        if (!credentialsConfigured()) {
            throw new EmailDeliveryException("Не задан API-ключ NotiSend", false);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("from_email", email.fromAddress());
        if (email.fromName() != null && !email.fromName().isBlank()) {
            body.put("from_name", email.fromName());
        }
        body.put("to", email.to());
        body.put("subject", email.subject());
        body.put("html", email.html());
        if (email.replyTo() != null && !email.replyTo().isBlank()) {
            body.put("smtp_headers", Map.of("Reply-To", email.replyTo()));
        }

        SendResult result;
        try {
            result = webClient.post()
                    .uri("/email/messages")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .exchangeToMono(response -> response.bodyToMono(String.class)
                            .defaultIfEmpty("")
                            .map(text -> new SendResult(response.statusCode().value(), text)))
                    .timeout(TIMEOUT)
                    .block();
        } catch (RuntimeException e) {
            throw new EmailDeliveryException("NotiSend недоступен: " + e.getMessage(), false, e);
        }
        if (result == null) {
            throw new EmailDeliveryException("NotiSend: пустой ответ", false);
        }
        if (result.status() >= 200 && result.status() < 300) {
            return messageId(result.body());
        }
        boolean permanent = result.status() >= 400 && result.status() < 500 && result.status() != 429;
        throw new EmailDeliveryException("NotiSend вернул HTTP " + result.status() + ": " + crop(result.body()), permanent);
    }

    private String messageId(String body) {
        try {
            JsonNode id = objectMapper.readTree(body).path("id");
            return id.isMissingNode() || id.isNull() ? null : id.asText();
        } catch (Exception e) {
            return null;
        }
    }

    private static String crop(String text) {
        return text != null && text.length() > 500 ? text.substring(0, 500) : text;
    }

    private record SendResult(int status, String body) {
    }
}
