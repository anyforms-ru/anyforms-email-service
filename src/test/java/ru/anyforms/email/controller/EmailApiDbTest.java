package ru.anyforms.email.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfSystemProperty(named = "emailDb", matches = "true")
@TestPropertySource(properties = {
        "spring.datasource.url=${email.db.url:jdbc:postgresql://localhost:5475/email_test}",
        "spring.datasource.username=postgres",
        "spring.datasource.password=",
        "EMAIL_SERVICE_TOKEN=test-token",
        "EMAIL_SES_ENDPOINT=https://postbox.cloud.yandex.net",
        "EMAIL_SES_REGION=ru-central1",
        "EMAIL_SES_ACCESS_KEY_ID=",
        "EMAIL_SES_SECRET_ACCESS_KEY=",
        "EMAIL_NOTISEND_API_KEY=",
        "POSTGRES_URL=unused",
        "POSTGRES_USER=unused",
        "POSTGRES_PASSWORD=unused",
        "email.queue.initial-delay-ms=3600000",
})
class EmailApiDbTest {

    private static final String RAW = """
            {"type":"SYSTEM","to":"client@example.com","subject":"Тема","html":"<p>hi</p>"}
            """;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clean() {
        jdbcTemplate.update("delete from email_message");
        jdbcTemplate.update("delete from email_template");
    }

    @Test
    void withoutTokenIsUnauthorized() throws Exception {
        mvc.perform(post("/api/v1/emails").contentType(MediaType.APPLICATION_JSON).content(RAW)
                        .header("Idempotency-Key", "k1").header("X-Client-Id", "anyforms-back"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void sameKeyIsQueuedOnce() throws Exception {
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/v1/emails").contentType(MediaType.APPLICATION_JSON).content(RAW)
                            .header("X-Auth-Token", "test-token")
                            .header("Idempotency-Key", "task:1").header("X-Client-Id", "anyforms-back"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("QUEUED"))
                    .andExpect(jsonPath("$.duplicate").value(i == 1));
        }
        assertEquals(1, jdbcTemplate.queryForObject("select count(*) from email_message", Integer.class));

        mvc.perform(post("/api/v1/emails").contentType(MediaType.APPLICATION_JSON)
                        .content(RAW.replace("hi", "bye"))
                        .header("X-Auth-Token", "test-token")
                        .header("Idempotency-Key", "task:1").header("X-Client-Id", "anyforms-back"))
                .andExpect(status().isConflict());
    }

    @Test
    void templateIsUploadedAndUsed() throws Exception {
        mvc.perform(put("/api/v1/templates/login-code").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer test-token")
                        .content("{\"subject\":\"Код {{code}}\",\"html\":\"<b>{{code}}</b>\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.variables[0]").value("code"));

        mvc.perform(post("/api/v1/emails").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Auth-Token", "test-token")
                        .header("Idempotency-Key", "login:1").header("X-Client-Id", "edu-anyforms-back")
                        .content("{\"type\":\"SYSTEM\",\"to\":\"a@b.ru\",\"templateCode\":\"login-code\",\"variables\":{}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Не переданы переменные шаблона: code"));

        mvc.perform(post("/api/v1/emails").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Auth-Token", "test-token")
                        .header("Idempotency-Key", "login:1").header("X-Client-Id", "edu-anyforms-back")
                        .content("{\"type\":\"SYSTEM\",\"to\":\"a@b.ru\",\"templateCode\":\"login-code\",\"variables\":{\"code\":\"<1>\"}}"))
                .andExpect(status().isOk());

        assertEquals("<b>&lt;1&gt;</b>", jdbcTemplate.queryForObject("select html from email_message", String.class));
    }

    @Test
    void sendersAreListedAndValidated() throws Exception {
        mvc.perform(get("/api/v1/senders").header("X-Auth-Token", "test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("SYSTEM"))
                .andExpect(jsonPath("$[0].providerConfigured").value(false));

        mvc.perform(put("/api/v1/senders/SYSTEM").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Auth-Token", "test-token")
                        .content("{\"fromAddress\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void bulkQueuesAllInOneTransaction() throws Exception {
        mvc.perform(put("/api/v1/templates/promo").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Auth-Token", "test-token")
                        .content("{\"subject\":\"Скидка {{percent}}%\",\"html\":\"<p>{{percent}}</p>\"}"))
                .andExpect(status().isOk());

        String body = """
                {"batchKey":"b1","type":"MARKETING","templateCode":"promo","variables":{"percent":"10"},
                 "recipients":["a@b.ru, c@d.ru; A@B.ru"]}
                """;
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/v1/emails/bulk").contentType(MediaType.APPLICATION_JSON).content(body)
                            .header("X-Auth-Token", "test-token").header("X-Client-Id", "anyforms-back"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").value(2))
                    .andExpect(jsonPath("$.queued").value(i == 0 ? 2 : 0))
                    .andExpect(jsonPath("$.duplicates").value(i == 0 ? 0 : 2));
        }
        assertEquals(2, jdbcTemplate.queryForObject(
                "select count(*) from email_message where status = 'QUEUED' and subject = 'Скидка 10%'", Integer.class));

        mvc.perform(post("/api/v1/emails/bulk").contentType(MediaType.APPLICATION_JSON)
                        .content(body.replace("b1", "b2").replace("c@d.ru", "x@y.ru, e@f.ru").replace("\"10\"", "\"20\""))
                        .header("X-Auth-Token", "test-token").header("X-Client-Id", "anyforms-back"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queued").value(3));

        // Второй адрес из b1 с другими переменными — конфликт, и новый адрес x2 из этого же запроса не остаётся в очереди
        mvc.perform(post("/api/v1/emails/bulk").contentType(MediaType.APPLICATION_JSON)
                        .content(body.replace("a@b.ru, c@d.ru", "x2@y.ru, a@b.ru, c@d.ru").replace("\"10\"", "\"30\""))
                        .header("X-Auth-Token", "test-token").header("X-Client-Id", "anyforms-back"))
                .andExpect(status().isConflict());
        assertEquals(5, jdbcTemplate.queryForObject("select count(*) from email_message", Integer.class));
    }
}
