package ru.anyforms.email.service.provider.impl;

import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.anyforms.email.exception.EmailDeliveryException;
import ru.anyforms.email.model.EmailType;
import ru.anyforms.email.service.provider.EmailProvider;
import ru.anyforms.email.service.provider.OutgoingEmail;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.AccountSuspendedException;
import software.amazon.awssdk.services.sesv2.model.BadRequestException;
import software.amazon.awssdk.services.sesv2.model.Destination;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.MailFromDomainNotVerifiedException;
import software.amazon.awssdk.services.sesv2.model.MessageRejectedException;
import software.amazon.awssdk.services.sesv2.model.NotFoundException;
import software.amazon.awssdk.services.sesv2.model.RawMessage;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;
import software.amazon.awssdk.services.sesv2.model.SendEmailResponse;
import software.amazon.awssdk.services.sesv2.model.SesV2Exception;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Properties;

@Slf4j
@Component
class SesEmailProvider implements EmailProvider {

    private static final String CHARSET = StandardCharsets.UTF_8.name();

    private final SesV2Client client;
    private final boolean credentialsConfigured;
    private final Session session = Session.getInstance(new Properties());

    SesEmailProvider(SesV2Client client,
                     @Value("${email.ses.access-key-id}") String accessKeyId,
                     @Value("${email.ses.secret-access-key}") String secretAccessKey) {
        this.client = client;
        this.credentialsConfigured = accessKeyId != null && !accessKeyId.isBlank()
                && secretAccessKey != null && !secretAccessKey.isBlank();
    }

    @Override
    public EmailType type() {
        return EmailType.SYSTEM;
    }

    @Override
    public String name() {
        return "Yandex Cloud Postbox (AWS SES API v2)";
    }

    @Override
    public boolean credentialsConfigured() {
        return credentialsConfigured;
    }

    @Override
    public String send(OutgoingEmail email) {
        if (!credentialsConfigured) {
            throw new EmailDeliveryException("Не заданы ключи доступа к SES/Postbox", false);
        }
        SendEmailRequest request = SendEmailRequest.builder()
                .fromEmailAddress(email.fromAddress())
                .destination(Destination.builder().toAddresses(email.to()).build())
                .content(EmailContent.builder()
                        .raw(RawMessage.builder().data(SdkBytes.fromByteArray(buildMime(email))).build())
                        .build())
                .build();
        try {
            SendEmailResponse response = client.sendEmail(request);
            return response.messageId();
        } catch (MessageRejectedException | MailFromDomainNotVerifiedException | BadRequestException
                 | NotFoundException | AccountSuspendedException e) {
            throw new EmailDeliveryException("SES отклонил письмо: " + errorMessage(e), true, e);
        } catch (SesV2Exception e) {
            throw new EmailDeliveryException("SES вернул ошибку: " + errorMessage(e), false, e);
        } catch (SdkClientException e) {
            throw new EmailDeliveryException("SES недоступен: " + e.getMessage(), false, e);
        } catch (SdkException e) {
            throw new EmailDeliveryException("Ошибка SES: " + e.getMessage(), false, e);
        }
    }

    byte[] buildMime(OutgoingEmail email) {
        try {
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(email.fromAddress(), email.fromName(), CHARSET));
            message.setRecipient(Message.RecipientType.TO, new InternetAddress(email.to(), true));
            if (email.replyTo() != null && !email.replyTo().isBlank()) {
                message.setReplyTo(new InternetAddress[]{new InternetAddress(email.replyTo(), true)});
            }
            message.setSubject(email.subject(), CHARSET);
            message.setSentDate(new Date());
            message.setText(email.html(), CHARSET, "html");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            message.writeTo(out);
            return out.toByteArray();
        } catch (AddressException e) {
            throw new EmailDeliveryException("Некорректный адрес в письме: " + e.getMessage(), true, e);
        } catch (MessagingException | IOException e) {
            throw new EmailDeliveryException("Не получилось собрать письмо: " + e.getMessage(), true, e);
        }
    }

    private static String errorMessage(SesV2Exception e) {
        if (e.awsErrorDetails() != null && e.awsErrorDetails().errorMessage() != null) {
            return e.awsErrorDetails().errorCode() + ": " + e.awsErrorDetails().errorMessage();
        }
        return e.getMessage();
    }
}
