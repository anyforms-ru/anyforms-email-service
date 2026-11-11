package ru.anyforms.email.service.provider;

import org.springframework.stereotype.Component;
import ru.anyforms.email.model.EmailType;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class EmailProviderRegistry {

    private final Map<EmailType, EmailProvider> byType = new EnumMap<>(EmailType.class);

    public EmailProviderRegistry(List<EmailProvider> providers) {
        for (EmailProvider provider : providers) {
            if (byType.put(provider.type(), provider) != null) {
                throw new IllegalStateException("Два провайдера для писем " + provider.type());
            }
        }
        for (EmailType type : EmailType.values()) {
            if (!byType.containsKey(type)) {
                throw new IllegalStateException("Нет провайдера для писем " + type);
            }
        }
    }

    public EmailProvider get(EmailType type) {
        return byType.get(type);
    }
}
