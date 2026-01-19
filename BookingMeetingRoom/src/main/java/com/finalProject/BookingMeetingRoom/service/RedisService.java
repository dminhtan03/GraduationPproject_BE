package com.finalProject.BookingMeetingRoom.service;

import com.finalProject.BookingMeetingRoom.common.exception.CustomException;
import com.finalProject.BookingMeetingRoom.common.payload.ResponseCode;
import com.finalProject.BookingMeetingRoom.model.entity.RefreshToken;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class RedisService {

    private final RedisTemplate<String, Object> redisTemplate;
    private static final String REFRESH_TOKEN_PREFIX = "REDIS_KEY:";

    public void setValue(String key, Object value, long timeout, TimeUnit unit) {
        redisTemplate.opsForValue().set(key, value, timeout, unit);
    }

    public Object getValue(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    public void delete(String key) {
        redisTemplate.delete(key);
    }

    public void cacheRefreshToken(RefreshToken refreshToken) {
        try {
            String key = REFRESH_TOKEN_PREFIX + refreshToken.getId();
            long ttl = refreshToken.getExpiresAt().toEpochSecond(java.time.ZoneOffset.UTC)
                    - java.time.Instant.now().getEpochSecond();

            redisTemplate.opsForValue().set(key, refreshToken, ttl, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new CustomException(ResponseCode.CACHE_FAILED);
        }
    }

    public RefreshToken getCacheRefreshToken(String refreshTokenId) {
        try {
            String key = REFRESH_TOKEN_PREFIX + refreshTokenId;
            Object cached = redisTemplate.opsForValue().get(key);
            return cached instanceof RefreshToken ? (RefreshToken) cached : null;
        } catch (Exception e) {
            throw new CustomException(ResponseCode.CACHE_FAILED);
        }
    }

    public void deleteCacheToken(String refreshTokenId) {
        try {
            redisTemplate.delete(REFRESH_TOKEN_PREFIX + refreshTokenId);
        } catch (Exception e) {
            throw new CustomException(ResponseCode.CACHE_FAILED);
        }
    }
}
