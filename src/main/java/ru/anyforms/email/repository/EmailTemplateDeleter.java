package ru.anyforms.email.repository;

public interface EmailTemplateDeleter {
    boolean delete(String code);
}
