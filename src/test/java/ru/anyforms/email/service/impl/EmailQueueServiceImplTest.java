package ru.anyforms.email.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import ru.anyforms.email.dto.BulkEmailRequest;
import ru.anyforms.email.dto.BulkEmailResponse;
import ru.anyforms.email.dto.RenderedEmailDTO;
import ru.anyforms.email.dto.SendEmailRequest;
import ru.anyforms.email.dto.SendEmailResponse;
import ru.anyforms.email.model.EmailMessage;
import ru.anyforms.email.model.EmailMessageStatus;
import ru.anyforms.email.model.EmailType;
import ru.anyforms.email.repository.GetterEmailMessage;
import ru.anyforms.email.repository.SaverEmailMessage;
import ru.anyforms.email.service.EmailTemplateService;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailQueueServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-11-11T10:00:00Z");
    private static final String CLIENT = "anyforms-back";
    private static final String KEY = "task:42";

    private final GetterEmailMessage getter = mock(GetterEmailMessage.class);
    private final SaverEmailMessage saver = mock(SaverEmailMessage.class);
    private final EmailTemplateService templates = mock(EmailTemplateService.class);
    private EmailQueueServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new EmailQueueServiceImpl(getter, saver, templates, Clock.fixed(NOW, ZoneOffset.UTC));
        when(getter.getByClientAndIdempotencyKey(CLIENT, KEY)).thenReturn(Optional.empty());
        when(saver.insertIfAbsent(any())).thenReturn(true);
    }

    private static SendEmailRequest raw(String html) {
        return new SendEmailRequest(EmailType.SYSTEM, "client@example.com", "Команда di_gips", null, null, null,
                "Ваш заказ\nоформлен", html);
    }

    @Test
    void queuesRawLetterWithRenderedSubject() {
        SendEmailResponse response = service.enqueue(CLIENT, KEY, raw("<p>hi</p>"));

        ArgumentCaptor<EmailMessage> captor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(saver).insertIfAbsent(captor.capture());
        EmailMessage saved = captor.getValue();
        assertFalse(response.duplicate());
        assertEquals(EmailMessageStatus.QUEUED, response.status());
        assertEquals(saved.getId(), response.id());
        assertEquals("Ваш заказ оформлен", saved.getSubject());
        assertEquals("<p>hi</p>", saved.getHtml());
        assertEquals("Команда di_gips", saved.getFromName());
        assertEquals(NOW, saved.getNextAttemptAt());
        assertEquals(64, saved.getRequestHash().length());
    }

    @Test
    void rendersTemplateWhenCodeGiven() {
        Map<String, Object> vars = Map.of("code", "123456");
        when(templates.render("login-code", vars)).thenReturn(new RenderedEmailDTO("Код", "<b>123456</b>"));

        service.enqueue(CLIENT, KEY, new SendEmailRequest(EmailType.SYSTEM, "a@b.ru", null, null, "login-code", vars, null, null));

        ArgumentCaptor<EmailMessage> captor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(saver).insertIfAbsent(captor.capture());
        assertEquals("login-code", captor.getValue().getTemplateCode());
        assertEquals("<b>123456</b>", captor.getValue().getHtml());
    }

    @Test
    void sameKeySameBodyReturnsExistingWithoutNewRow() {
        service.enqueue(CLIENT, KEY, raw("<p>hi</p>"));
        ArgumentCaptor<EmailMessage> captor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(saver).insertIfAbsent(captor.capture());
        EmailMessage first = captor.getValue();
        first.setStatus(EmailMessageStatus.SENT);
        when(getter.getByClientAndIdempotencyKey(CLIENT, KEY)).thenReturn(Optional.of(first));

        SendEmailResponse again = service.enqueue(CLIENT, KEY, raw("<p>hi</p>"));

        assertTrue(again.duplicate());
        assertEquals(first.getId(), again.id());
        assertEquals(EmailMessageStatus.SENT, again.status());
    }

    @Test
    void sameKeyDifferentBodyIsConflict() {
        service.enqueue(CLIENT, KEY, raw("<p>hi</p>"));
        ArgumentCaptor<EmailMessage> captor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(saver).insertIfAbsent(captor.capture());
        when(getter.getByClientAndIdempotencyKey(CLIENT, KEY)).thenReturn(Optional.of(captor.getValue()));

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.enqueue(CLIENT, KEY, raw("<p>другое</p>")));

        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
    }

    @Test
    void lostInsertRaceReturnsWinner() {
        EmailMessage[] winner = new EmailMessage[1];
        when(saver.insertIfAbsent(any())).thenAnswer(inv -> {
            winner[0] = inv.getArgument(0);
            when(getter.getByClientAndIdempotencyKey(CLIENT, KEY)).thenReturn(Optional.of(winner[0]));
            return false;
        });

        SendEmailResponse response = service.enqueue(CLIENT, KEY, raw("<p>hi</p>"));

        assertTrue(response.duplicate());
        assertEquals(winner[0].getId(), response.id());
    }

    @Test
    void rejectsBadClientKeyAndContent() {
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> service.enqueue("Anyforms Back", KEY, raw("<p/>"))).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> service.enqueue(CLIENT, " ", raw("<p/>"))).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> service.enqueue(CLIENT, KEY, new SendEmailRequest(EmailType.SYSTEM, "a@b.ru", null, null, null, null, "s", null))).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> service.enqueue(CLIENT, KEY, new SendEmailRequest(EmailType.SYSTEM, "a@b.ru", null, null, "t", null, "s", "<p/>"))).getStatusCode());
        verify(saver, never()).insertIfAbsent(any());
    }

    private static BulkEmailRequest bulk(String... recipients) {
        return new BulkEmailRequest("promo-1", EmailType.MARKETING, "promo", Map.of("name", "друг"),
                null, null, List.of(recipients));
    }

    @Test
    void bulkQueuesEveryUniqueAddressOnce() {
        when(templates.render("promo", Map.of("name", "друг"))).thenReturn(new RenderedEmailDTO("Скидка", "<p>друг</p>"));

        BulkEmailResponse response = service.enqueueBulk(CLIENT, bulk("a@b.ru, C@d.ru;\n c@D.ru  e@f.com"));

        ArgumentCaptor<EmailMessage> captor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(saver, times(3)).insertIfAbsent(captor.capture());
        verify(templates, times(1)).render(anyString(), any());
        assertEquals(new BulkEmailResponse("promo-1", 3, 3, 0), response);
        assertEquals(List.of("a@b.ru", "C@d.ru", "e@f.com"),
                captor.getAllValues().stream().map(EmailMessage::getToAddress).toList());
        EmailMessage first = captor.getAllValues().get(0);
        assertEquals("bulk:promo-1:a@b.ru", first.getIdempotencyKey());
        assertEquals(EmailType.MARKETING, first.getType());
        assertEquals("promo", first.getTemplateCode());
        assertEquals("Скидка", first.getSubject());
        assertEquals(EmailMessageStatus.QUEUED, first.getStatus());
    }

    @Test
    void bulkRejectsWholeListWithInvalidAddress() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.enqueueBulk(CLIENT, bulk("a@b.ru, not-an-email, x@y")));

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
        assertTrue(e.getReason().contains("not-an-email"));
        assertTrue(e.getReason().contains("x@y"));
        verify(saver, never()).insertIfAbsent(any());
        verify(templates, never()).render(anyString(), any());
    }

    @Test
    void bulkRepeatCountsDuplicatesAndConflictsOnChangedContent() {
        when(templates.render(eq("promo"), any())).thenReturn(new RenderedEmailDTO("Скидка", "<p>x</p>"));
        ArgumentCaptor<EmailMessage> captor = ArgumentCaptor.forClass(EmailMessage.class);
        service.enqueueBulk(CLIENT, bulk("a@b.ru"));
        verify(saver).insertIfAbsent(captor.capture());
        EmailMessage stored = captor.getValue();

        when(saver.insertIfAbsent(any())).thenReturn(false);
        when(getter.getByClientAndIdempotencyKey(CLIENT, "bulk:promo-1:a@b.ru")).thenReturn(Optional.of(stored));
        assertEquals(new BulkEmailResponse("promo-1", 1, 0, 1), service.enqueueBulk(CLIENT, bulk("A@B.ru")));

        BulkEmailRequest changed = new BulkEmailRequest("promo-1", EmailType.MARKETING, "promo", Map.of("name", "другой"),
                null, null, List.of("a@b.ru"));
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> service.enqueueBulk(CLIENT, changed));
        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
    }

    @Test
    void bulkKeyFitsColumnForLongAddress() {
        String longAddress = "a".repeat(240) + "@example.com";
        String key = EmailQueueServiceImpl.bulkKey("promo-1", longAddress);
        assertTrue(key.length() <= EmailQueueServiceImpl.MAX_KEY_LENGTH);
        assertEquals(key, EmailQueueServiceImpl.bulkKey("promo-1", longAddress.toUpperCase()));
    }
}
