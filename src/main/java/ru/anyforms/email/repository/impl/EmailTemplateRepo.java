package ru.anyforms.email.repository.impl;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.anyforms.email.model.EmailTemplate;

import java.util.List;

@Repository
interface EmailTemplateRepo extends JpaRepository<EmailTemplate, String> {

    List<EmailTemplate> findAllByOrderByCodeAsc();
}
