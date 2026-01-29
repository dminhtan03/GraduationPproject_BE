package com.finalProject.BookingMeetingRoom.common.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
public class RedisProperties {

    @Value("${redis.host}")
    private String host;

    @Value("${redis.port}")
    private int port;
}
