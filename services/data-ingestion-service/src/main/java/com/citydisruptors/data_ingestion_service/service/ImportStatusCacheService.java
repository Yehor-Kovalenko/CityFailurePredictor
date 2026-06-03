package com.citydisruptors.data_ingestion_service.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class ImportStatusCacheService {

    private final StringRedisTemplate redis;

    public ImportStatusCacheService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void updateStatus(String fileName, long importedRows, long skippedRows, boolean completed) {
        String key = "ingestion:import:" + fileName;

        redis.opsForHash().put(key, "fileName", fileName);
        redis.opsForHash().put(key, "importedRows", String.valueOf(importedRows));
        redis.opsForHash().put(key, "skippedRows", String.valueOf(skippedRows));
        redis.opsForHash().put(key, "completed", String.valueOf(completed));

        redis.expire(key, Duration.ofHours(12));
    }
}