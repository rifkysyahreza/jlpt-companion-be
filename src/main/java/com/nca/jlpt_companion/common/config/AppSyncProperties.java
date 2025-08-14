package com.nca.jlpt_companion.common.config;


import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@ConfigurationProperties(prefix = "app.sync")
public class AppSyncProperties {
    private long maxSkewHours = 48;

    public void setMaxSkewHours(long v) { this.maxSkewHours = v; }
}
