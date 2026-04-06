package com.finalProject.BookingMeetingRoom.service.redis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisServiceImplTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private RedisServiceImpl redisService;

    private RefreshToken testRefreshToken;
    private final String TEST_KEY = "test-key";
    private final String TEST_VALUE = "test-value";
    private final String REFRESH_TOKEN_PREFIX = "REDIS_KEY:";

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        testRefreshToken = new RefreshToken();
        testRefreshToken.setId("token-123");
        testRefreshToken.setToken("refresh-token-value");
        testRefreshToken.setExpiresAt(LocalDateTime.now().plusHours(1));
        testRefreshToken.setRevoked(false);
    }

    // ==================== setValue Tests ====================

    /**
     * Test successful setValue operation
     */
    @Test
    void testSetValue_Success() {
        // Arrange
        long timeout = 60;
        TimeUnit unit = TimeUnit.MINUTES;

        // Act
        redisService.setValue(TEST_KEY, TEST_VALUE, timeout, unit);

        // Assert
        verify(redisTemplate, times(1)).opsForValue();
        verify(valueOperations, times(1)).set(TEST_KEY, TEST_VALUE, timeout, unit);
    }

    /**
     * Test setValue with different timeout units
     */
    @Test
    void testSetValue_Success_WithDifferentTimeUnits() {
        // Test with SECONDS
        redisService.setValue(TEST_KEY, TEST_VALUE, 30, TimeUnit.SECONDS);
        verify(valueOperations, times(1)).set(TEST_KEY, TEST_VALUE, 30, TimeUnit.SECONDS);

        // Test with HOURS
        redisService.setValue(TEST_KEY, TEST_VALUE, 2, TimeUnit.HOURS);
        verify(valueOperations, times(1)).set(TEST_KEY, TEST_VALUE, 2, TimeUnit.HOURS);

        // Test with DAYS
        redisService.setValue(TEST_KEY, TEST_VALUE, 1, TimeUnit.DAYS);
        verify(valueOperations, times(1)).set(TEST_KEY, TEST_VALUE, 1, TimeUnit.DAYS);
    }

    /**
     * Test setValue with null value
     */
    @Test
    void testSetValue_Success_WithNullValue() {
        // Act
        redisService.setValue(TEST_KEY, null, 60, TimeUnit.MINUTES);

        // Assert
        verify(valueOperations, times(1)).set(TEST_KEY, null, 60, TimeUnit.MINUTES);
    }

    /**
     * Test setValue with zero timeout
     */
    @Test
    void testSetValue_Success_WithZeroTimeout() {
        // Act
        redisService.setValue(TEST_KEY, TEST_VALUE, 0, TimeUnit.SECONDS);

        // Assert
        verify(valueOperations, times(1)).set(TEST_KEY, TEST_VALUE, 0, TimeUnit.SECONDS);
    }

    // ==================== getValue Tests ====================

    /**
     * Test successful getValue operation
     */
    @Test
    void testGetValue_Success_ReturnsValue() {
        // Arrange
        when(valueOperations.get(TEST_KEY)).thenReturn(TEST_VALUE);

        // Act
        Object result = redisService.getValue(TEST_KEY);

        // Assert
        assertEquals(TEST_VALUE, result);
        verify(redisTemplate, times(1)).opsForValue();
        verify(valueOperations, times(1)).get(TEST_KEY);
    }

    /**
     * Test getValue when key does not exist
     */
    @Test
    void testGetValue_Success_ReturnsNull() {
        // Arrange
        when(valueOperations.get(TEST_KEY)).thenReturn(null);

        // Act
        Object result = redisService.getValue(TEST_KEY);

        // Assert
        assertNull(result);
        verify(valueOperations, times(1)).get(TEST_KEY);
    }

    /**
     * Test getValue with different object types
     */
    @Test
    void testGetValue_Success_WithDifferentObjectTypes() {
        // Test with String
        when(valueOperations.get("string-key")).thenReturn("string-value");
        Object stringResult = redisService.getValue("string-key");
        assertEquals("string-value", stringResult);

        // Test with Integer
        when(valueOperations.get("int-key")).thenReturn(123);
        Object intResult = redisService.getValue("int-key");
        assertEquals(123, intResult);

        // Test with RefreshToken object
        when(valueOperations.get("token-key")).thenReturn(testRefreshToken);
        Object tokenResult = redisService.getValue("token-key");
        assertEquals(testRefreshToken, tokenResult);
    }

    // ==================== cacheRefreshToken Tests ====================

    /**
     * Test successful cacheRefreshToken operation
     */
    @Test
    void testCacheRefreshToken_Success() {
        // Arrange
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(1);
        testRefreshToken.setExpiresAt(expiresAt);

        long expectedTtl = expiresAt.toEpochSecond(ZoneOffset.UTC)
                - java.time.Instant.now().getEpochSecond();

        // Act
        redisService.cacheRefreshToken(testRefreshToken);

        // Assert
        String expectedKey = REFRESH_TOKEN_PREFIX + testRefreshToken.getId();

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> valueCaptor = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Long> ttlCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<TimeUnit> unitCaptor = ArgumentCaptor.forClass(TimeUnit.class);

        verify(valueOperations, times(1)).set(
                keyCaptor.capture(),
                valueCaptor.capture(),
                ttlCaptor.capture(),
                unitCaptor.capture()
        );

        assertEquals(expectedKey, keyCaptor.getValue());
        assertEquals(testRefreshToken, valueCaptor.getValue());
        assertTrue(Math.abs(expectedTtl - ttlCaptor.getValue()) <= 1); // Allow 1 second difference
        assertEquals(TimeUnit.SECONDS, unitCaptor.getValue());
    }

    /**
     * Test cacheRefreshToken when RedisTemplate throws exception
     */
    @Test
    void testCacheRefreshToken_RedisThrowsException_ThrowsCacheFailed() {
        // Arrange
        doThrow(new RuntimeException("Redis connection failed"))
                .when(valueOperations).set(anyString(), any(), anyLong(), any(TimeUnit.class));

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            redisService.cacheRefreshToken(testRefreshToken);
        });

        assertEquals(ResponseCode.CACHE_FAILED, exception.getResponseCode());

        verify(valueOperations, times(1)).set(anyString(), any(), anyLong(), any(TimeUnit.class));
    }

    /**
     * Test cacheRefreshToken with token expiring soon
     */
    @Test
    void testCacheRefreshToken_Success_WithTokenExpiringSoon() {
        // Arrange - Token expires in 30 seconds
        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(30);
        testRefreshToken.setExpiresAt(expiresAt);

        // Act
        redisService.cacheRefreshToken(testRefreshToken);

        // Assert
        ArgumentCaptor<Long> ttlCaptor = ArgumentCaptor.forClass(Long.class);
        verify(valueOperations, times(1)).set(anyString(), any(), ttlCaptor.capture(), any(TimeUnit.class));

        long capturedTtl = ttlCaptor.getValue();
    }

    /**
     * Test cacheRefreshToken with already expired token
     */
    @Test
    void testCacheRefreshToken_Success_WithExpiredToken() {
        // Arrange - Token expired 1 hour ago
        LocalDateTime expiresAt = LocalDateTime.now().minusHours(1);
        testRefreshToken.setExpiresAt(expiresAt);

        // Act
        redisService.cacheRefreshToken(testRefreshToken);

        // Assert
        ArgumentCaptor<Long> ttlCaptor = ArgumentCaptor.forClass(Long.class);
        verify(valueOperations, times(1)).set(anyString(), any(), ttlCaptor.capture(), any(TimeUnit.class));
    }

    // ==================== getCacheRefreshToken Tests ====================

    /**
     * Test successful getCacheRefreshToken operation
     */
    @Test
    void testGetCacheRefreshToken_Success_ReturnsToken() {
        // Arrange
        String refreshTokenId = "token-123";
        String expectedKey = REFRESH_TOKEN_PREFIX + refreshTokenId;
        when(valueOperations.get(expectedKey)).thenReturn(testRefreshToken);

        // Act
        RefreshToken result = redisService.getCacheRefreshToken(refreshTokenId);

        // Assert
        assertNotNull(result);
        assertEquals(testRefreshToken, result);
        assertEquals("token-123", result.getId());
        assertEquals("refresh-token-value", result.getToken());

        verify(valueOperations, times(1)).get(expectedKey);
    }

    /**
     * Test getCacheRefreshToken when token does not exist
     */
    @Test
    void testGetCacheRefreshToken_Success_ReturnsNull() {
        // Arrange
        String refreshTokenId = "nonexistent-token";
        String expectedKey = REFRESH_TOKEN_PREFIX + refreshTokenId;
        when(valueOperations.get(expectedKey)).thenReturn(null);

        // Act
        RefreshToken result = redisService.getCacheRefreshToken(refreshTokenId);

        // Assert
        assertNull(result);
        verify(valueOperations, times(1)).get(expectedKey);
    }

    /**
     * Test getCacheRefreshToken when cached object is not RefreshToken
     */
    @Test
    void testGetCacheRefreshToken_Success_ReturnsNullForWrongType() {
        // Arrange
        String refreshTokenId = "token-123";
        String expectedKey = REFRESH_TOKEN_PREFIX + refreshTokenId;
        when(valueOperations.get(expectedKey)).thenReturn("not-a-refresh-token");

        // Act
        RefreshToken result = redisService.getCacheRefreshToken(refreshTokenId);

        // Assert
        assertNull(result);
        verify(valueOperations, times(1)).get(expectedKey);
    }

    /**
     * Test getCacheRefreshToken when Redis throws exception
     */
    @Test
    void testGetCacheRefreshToken_RedisThrowsException_ThrowsCacheFailed() {
        // Arrange
        String refreshTokenId = "token-123";
        String expectedKey = REFRESH_TOKEN_PREFIX + refreshTokenId;
        when(valueOperations.get(expectedKey))
                .thenThrow(new RuntimeException("Redis connection failed"));

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            redisService.getCacheRefreshToken(refreshTokenId);
        });

        assertEquals(ResponseCode.CACHE_FAILED, exception.getResponseCode());

        verify(valueOperations, times(1)).get(expectedKey);
    }

    /**
     * Test getCacheRefreshToken with null tokenId
     */
    @Test
    void testGetCacheRefreshToken_Success_WithNullTokenId() {
        // Arrange
        String nullTokenId = null;
        String expectedKey = REFRESH_TOKEN_PREFIX + nullTokenId;
        when(valueOperations.get(expectedKey)).thenReturn(null);

        // Act
        RefreshToken result = redisService.getCacheRefreshToken(nullTokenId);

        // Assert
        assertNull(result);
        verify(valueOperations, times(1)).get(expectedKey);
    }

    // ==================== Integration Tests ====================

    /**
     * Test complete refresh token lifecycle (cache -> get -> delete)
     */
    @Test
    void testRefreshTokenLifecycle_Success() {
        // Arrange
        String tokenId = "lifecycle-token";
        testRefreshToken.setId(tokenId);
        String expectedKey = REFRESH_TOKEN_PREFIX + tokenId;

        when(valueOperations.get(expectedKey)).thenReturn(testRefreshToken);
        when(redisTemplate.delete(expectedKey)).thenReturn(true);

        // Act & Assert - Cache token
        redisService.cacheRefreshToken(testRefreshToken);
        verify(valueOperations, times(1)).set(eq(expectedKey), eq(testRefreshToken), anyLong(), eq(TimeUnit.SECONDS));

        // Act & Assert - Get token
        RefreshToken retrieved = redisService.getCacheRefreshToken(tokenId);
        assertNotNull(retrieved);
        assertEquals(testRefreshToken, retrieved);
        verify(valueOperations, times(1)).get(expectedKey);

        // Act & Assert - Delete token
        redisService.deleteCacheToken(tokenId);
        verify(redisTemplate, times(1)).delete(expectedKey);
    }

    /**
     * Test edge case with very long TTL calculation
     */
    @Test
    void testCacheRefreshToken_Success_WithVeryLongTTL() {
        // Arrange - Token expires in 1 year
        LocalDateTime expiresAt = LocalDateTime.now().plusYears(1);
        testRefreshToken.setExpiresAt(expiresAt);

        // Act
        redisService.cacheRefreshToken(testRefreshToken);

        // Assert
        ArgumentCaptor<Long> ttlCaptor = ArgumentCaptor.forClass(Long.class);
        verify(valueOperations, times(1)).set(anyString(), any(), ttlCaptor.capture(), any(TimeUnit.class));
    }
}