package ru.anyforms.email.repository;

import ru.anyforms.email.model.EmailMessage;

import java.time.Duration;
import java.util.List;

public interface EmailMessageQueue {
    List<EmailMessage> claimDue(int limit, Duration lock);
}
