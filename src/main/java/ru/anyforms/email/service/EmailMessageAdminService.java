package ru.anyforms.email.service;

import ru.anyforms.email.dto.EmailMessageDTO;
import ru.anyforms.email.dto.EmailMessageDetailsDTO;
import ru.anyforms.email.dto.QueueStatsDTO;
import ru.anyforms.email.model.EmailMessageStatus;
import ru.anyforms.email.model.EmailType;

import java.util.List;
import java.util.UUID;

public interface EmailMessageAdminService {

    List<EmailMessageDTO> list(EmailMessageStatus status, EmailType type, String client, Integer limit);

    EmailMessageDetailsDTO get(UUID id);

    QueueStatsDTO stats();

    EmailMessageDTO retry(UUID id);
}
