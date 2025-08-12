package com.nca.jlpt_companion.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Setter
@Getter
@ConfigurationProperties(prefix = "app.jwt")
public class AppJwtProperties {
    private String issuer;
    private String secret;
    private long accessExpMinutes;
    private long refreshExpDays;

}
