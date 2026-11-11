package ru.anyforms.email.service.provider.impl;

import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ru.anyforms.email.exception.EmailDeliveryException;
import ru.anyforms.email.service.provider.OutgoingEmail;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.MessageRejectedException;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;
import software.amazon.awssdk.services.sesv2.model.SendEmailResponse;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SesEmailProviderTest {

    private final SesV2Client client = mock(SesV2Client.class);
    private final SesEmailProvider provider = new SesEmailProvider(client, "key", "secret");

    private static OutgoingEmail email() {
        return new OutgoingEmail(UUID.randomUUID(), "client@example.com", "noreply@anyforms.ru", "Команда anyforms",
                "help@anyforms.ru", "Заказ #AB12 передан в СДЭК", "<p>Здравствуйте!</p>");
    }

    @Test
    void sendsRawMimeWithCyrillicHeadersAndReplyTo() throws Exception {
        when(client.sendEmail(any(SendEmailRequest.class))).thenReturn(SendEmailResponse.builder().messageId("m-1").build());

        assertEquals("m-1", provider.send(email()));

        ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(client).sendEmail(captor.capture());
        SendEmailRequest request = captor.getValue();
        assertEquals("noreply@anyforms.ru", request.fromEmailAddress());
        assertEquals(List.of("client@example.com"), request.destination().toAddresses());
        MimeMessage mime = new MimeMessage(Session.getInstance(new Properties()),
                new ByteArrayInputStream(request.content().raw().data().asByteArray()));
        assertEquals("Команда anyforms", ((InternetAddress) mime.getFrom()[0]).getPersonal());
        assertEquals("Заказ #AB12 передан в СДЭК", mime.getSubject());
        assertEquals("help@anyforms.ru", ((InternetAddress) mime.getReplyTo()[0]).getAddress());
    }

    @Test
    void rejectedLetterIsPermanent() {
        when(client.sendEmail(any(SendEmailRequest.class))).thenThrow(MessageRejectedException.builder().message("rejected").build());

        assertTrue(assertThrows(EmailDeliveryException.class, () -> provider.send(email())).isPermanent());
    }

    @Test
    void networkErrorIsRetryable() {
        when(client.sendEmail(any(SendEmailRequest.class))).thenThrow(SdkClientException.create("timeout"));

        assertFalse(assertThrows(EmailDeliveryException.class, () -> provider.send(email())).isPermanent());
    }

    @Test
    void withoutKeysNothingIsSent() {
        SesEmailProvider unconfigured = new SesEmailProvider(client, " ", "");

        assertFalse(unconfigured.credentialsConfigured());
        assertFalse(assertThrows(EmailDeliveryException.class, () -> unconfigured.send(email())).isPermanent());
        verifyNoInteractions(client);
    }
}
