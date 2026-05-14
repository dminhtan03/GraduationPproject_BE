package com.finalProject.BookingMeetingRoom.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "ai.platform")
public class AiPlatformProperties {
    private boolean enabled = true;
    private String appName = "AI Platform";
    private String appVersion = "0.1.0";
    private String baseUrl = "https://api.openai.com/v1";
    private String model = "gpt-4o-mini";
    private String apiKey;
    private int timeoutMs = 120000;
    private int sttTimeoutMs = 300000;

    public String getAppName() {
        return appName;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getAppVersion() {
        return appVersion;
    }

    public void setAppVersion(String appVersion) {
        this.appVersion = appVersion;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public int getSttTimeoutMs() {
        return sttTimeoutMs;
    }

    public void setSttTimeoutMs(int sttTimeoutMs) {
        this.sttTimeoutMs = sttTimeoutMs;
    }
}
