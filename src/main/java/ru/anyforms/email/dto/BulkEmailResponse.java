package ru.anyforms.email.dto;

/**
 * @param total      уникальных адресов в запросе
 * @param queued     новых писем поставлено в очередь
 * @param duplicates адресов, которым письмо этой рассылки уже стоит в очереди или отправлено
 */
public record BulkEmailResponse(String batchKey, int total, int queued, int duplicates) {
}
