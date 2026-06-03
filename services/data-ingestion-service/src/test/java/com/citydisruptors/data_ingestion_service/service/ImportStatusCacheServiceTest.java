package com.citydisruptors.data_ingestion_service.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

import static org.mockito.Mockito.*;

class ImportStatusCacheServiceTest {

    @Test
    void shouldUpdateImportStatusInRedis() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        HashOperations hashOperations = mock(HashOperations.class);

        when(redis.opsForHash()).thenReturn(hashOperations);

        ImportStatusCacheService service = new ImportStatusCacheService(redis);

        service.updateStatus("file.csv", 100, 2, false);

        String key = "ingestion:import:file.csv";

        verify(hashOperations).put(key, "fileName", "file.csv");
        verify(hashOperations).put(key, "importedRows", "100");
        verify(hashOperations).put(key, "skippedRows", "2");
        verify(hashOperations).put(key, "completed", "false");
        verify(redis).expire(key, Duration.ofHours(12));
    }
}