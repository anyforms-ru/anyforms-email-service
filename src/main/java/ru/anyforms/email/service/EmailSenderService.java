package ru.anyforms.email.service;

import ru.anyforms.email.dto.SendEmailResponse;
import ru.anyforms.email.dto.SenderDTO;
import ru.anyforms.email.dto.SenderUpdateRequest;
import ru.anyforms.email.model.EmailType;

import java.util.List;

public interface EmailSenderService {

    List<SenderDTO> list();

    SenderDTO update(EmailType type, SenderUpdateRequest request);

    SendEmailResponse sendTest(EmailType type, String to);
}
