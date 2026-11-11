package ru.anyforms.email.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.SesV2ClientBuilder;

import java.net.URI;

@Configuration
public class SesConfig {

    @Bean
    public SesV2Client sesClient(@Value("${email.ses.endpoint}") String endpoint,
                                 @Value("${email.ses.region}") String region,
                                 @Value("${email.ses.access-key-id}") String accessKeyId,
                                 @Value("${email.ses.secret-access-key}") String secretAccessKey) {
        SesV2ClientBuilder builder = SesV2Client.builder()
                .region(Region.of(region))
                .credentialsProvider(credentials(accessKeyId, secretAccessKey));
        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint.trim()));
        }
        return builder.build();
    }

    private static AwsCredentialsProvider credentials(String accessKeyId, String secretAccessKey) {
        if (accessKeyId == null || accessKeyId.isBlank() || secretAccessKey == null || secretAccessKey.isBlank()) {
            return AnonymousCredentialsProvider.create();
        }
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKeyId.trim(), secretAccessKey.trim()));
    }
}
