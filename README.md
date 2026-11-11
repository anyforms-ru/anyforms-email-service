# anyforms-email-service

Отдельный сервис отправки писем для всех бэков anyforms. Принимает письмо по HTTP, коммитит его в очередь (таблица Postgres `email_message`) и отвечает `200` только после коммита. Отправляет фоновый воркер.

Стек и устройство кода повторяют `anyforms-back`: Spring Boot 3.2, Java 21, Postgres + Flyway (`ddl-auto=validate`), пакеты `model / repository (Getter*/Saver* + *Manager) / service (интерфейс + package-private impl) / controller / dto / config`, env без дефолтов.

## Типы писем

| Тип | Провайдер | Для чего |
|---|---|---|
| `SYSTEM` | Yandex Cloud Postbox через AWS SDK SES v2 (`SesV2Client`, Raw MIME) | коды входа, чеки, статусы доставки, доступы к курсам |
| `MARKETING` | NotiSend | промо, рассылки |

Postbox совместим с API SES v2, поэтому используется штатный клиент AWS: endpoint `https://postbox.cloud.yandex.net`, регион `ru-central1`. Чтобы слать через настоящий AWS SES, достаточно оставить `EMAIL_SES_ENDPOINT` пустым и указать регион AWS.

Адрес отправителя, имя и Reply-To для каждого типа хранятся в таблице `email_sender` и меняются через API (`/api/v1/senders`) — из админки anyforms (`/admin/email`). Ключи провайдеров — только в env.

## API

Все ручки `/api/**` требуют межсервисный токен: `X-Auth-Token: <EMAIL_SERVICE_TOKEN>` или `Authorization: Bearer <EMAIL_SERVICE_TOKEN>`. Swagger: `/swagger-ui.html`. Health: `/actuator/health`.

### Отправка

```
POST /api/v1/emails
X-Client-Id: anyforms-back
Idempotency-Key: task:3f1c…
```

Готовое письмо:

```json
{ "type": "SYSTEM", "to": "client@example.com", "fromName": "Команда di_gips", "replyTo": null,
  "subject": "Ваш заказ оформлен", "html": "<p>…</p>" }
```

Или по шаблону:

```json
{ "type": "SYSTEM", "to": "client@example.com", "templateCode": "login-code", "variables": { "code": "123456" } }
```

Ответ `200 {"id": "…", "status": "QUEUED", "duplicate": false}`.

Идемпотентность — по паре `(X-Client-Id, Idempotency-Key)`, уникальный индекс в БД и `insert … on conflict do nothing`:

- тот же ключ и то же тело → `200`, то же `id`, `duplicate: true`, второго письма нет;
- тот же ключ, другое тело → `409`;
- ошибки запроса (нет шаблона, нет переменной, кривой адрес) → `400`/`404`, повторять бессмысленно.

Клиенту стоит повторять запрос с тем же ключом при сетевой ошибке и `5xx` — дубля не будет.

`GET /api/v1/emails/{id}` — статус письма.

### Шаблоны

`{{var}}` подставляется с HTML-экранированием, `{{{var}}}` — как есть (для готовых HTML-блоков). Переменные в теме не экранируются, переводы строк из темы убираются. Если переменной нет в `variables`, запрос получает `400` со списком недостающих.

| Метод | Путь | |
|---|---|---|
| GET | `/api/v1/templates` | список с переменными |
| GET | `/api/v1/templates/{code}` | шаблон целиком |
| PUT | `/api/v1/templates/{code}` | загрузить/заменить `{description, subject, html}` |
| DELETE | `/api/v1/templates/{code}` | удалить |
| POST | `/api/v1/templates/{code}/preview` | отрендерить `{variables}` без отправки |

Код шаблона: `[a-z0-9][a-z0-9._-]{0,99}`.

### Отправители и очередь

| Метод | Путь | |
|---|---|---|
| GET | `/api/v1/senders` | отправители `SYSTEM`/`MARKETING` и готовность провайдеров |
| PUT | `/api/v1/senders/{type}` | `{fromAddress, fromName, replyTo}` |
| POST | `/api/v1/senders/{type}/test` | `{to}` — тестовое письмо через очередь |
| GET | `/api/v1/messages?status=&type=&client=&limit=` | последние письма |
| GET | `/api/v1/messages/stats` | количество по статусам |
| GET | `/api/v1/messages/{id}` | письмо с HTML |
| POST | `/api/v1/messages/{id}/retry` | вернуть `FAILED` в очередь |

## Очередь

`QUEUED → SENDING → SENT | FAILED`.

- Воркер раз в `email.queue.fixed-delay-ms` берёт до `batch-size` писем через `select … for update skip locked`, переводит их в `SENDING` с `locked_until` — несколько инстансов не возьмут одно письмо.
- Письмо, зависшее в `SENDING` дольше `lock-seconds` (упал инстанс), берётся снова.
- Временная ошибка (сеть, 5xx, 429, троттлинг) → повтор через 1 мин, 5 мин, 15 мин, 1 ч, 3 ч; после `max-attempts` → `FAILED`.
- Постоянная ошибка (адрес отклонён, домен не верифицирован, 4xx NotiSend) → сразу `FAILED`.
- Не задан адрес отправителя → письмо ждёт и повторяется, пока его не настроят.
- `SENT` старше `retention-days` удаляются раз в сутки.

Доставка «как минимум один раз»: если провайдер принял письмо, а запись `SENT` не сохранилась, письмо уйдёт повторно после `lock-seconds`.

## Переменные окружения

Все обязательны, дефолтов нет.

| Env | Пример | |
|---|---|---|
| `POSTGRES_URL` | `jdbc:postgresql://anyforms-email-postgres:5432/anyforms_email` | своя БД сервиса |
| `POSTGRES_USER`, `POSTGRES_PASSWORD` | | |
| `EMAIL_SERVICE_TOKEN` | длинная случайная строка | тот же токен у всех клиентов; пусто — все ручки закрыты |
| `EMAIL_SES_ENDPOINT` | `https://postbox.cloud.yandex.net` | пусто — настоящий AWS SES |
| `EMAIL_SES_REGION` | `ru-central1` | |
| `EMAIL_SES_ACCESS_KEY_ID`, `EMAIL_SES_SECRET_ACCESS_KEY` | | статический ключ сервисного аккаунта с ролью `postbox.sender` |
| `EMAIL_NOTISEND_API_KEY` | | |

Технические константы (`email.queue.*`) лежат в `application.properties`.

## Сборка и тесты

```
JAVA_HOME=<JDK 21> mvn test
```

Тесты с живым Postgres (Flyway, очередь, HTTP) включаются флагом и ждут БД `email_test` на `localhost:5475` (или `-Demail.db.url=…`):

```
JAVA_HOME=<JDK 21> mvn test -DemailDb=true
```

Пуш в `main` собирает образ `ghcr.io/anyforms-ru/anyforms-email-service:latest` (`.github/workflows/deploy.yml`). Стек для сервера — `anyforms-infra/email-service` и `anyforms-infra/email-db`.
