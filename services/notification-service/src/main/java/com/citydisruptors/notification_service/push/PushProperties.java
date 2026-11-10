package com.citydisruptors.notification_service.push;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties("app.push")
public record PushProperties(String publicKey, String privateKey, String subject) {

    public boolean isConfigured() {
        return StringUtils.hasText(publicKey) && StringUtils.hasText(privateKey);
    }
}
