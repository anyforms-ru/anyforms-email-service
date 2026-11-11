package ru.anyforms.email.repository;

import java.time.Instant;

public interface EmailMessageCleaner {
    int deleteSentBefore(Instant before);
}
