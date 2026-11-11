package ru.anyforms.email.repository.impl;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.anyforms.email.model.EmailSender;
import ru.anyforms.email.model.EmailType;

@Repository
interface EmailSenderRepo extends JpaRepository<EmailSender, EmailType> {
}
