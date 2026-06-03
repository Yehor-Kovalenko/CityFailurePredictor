package com.citydisruptors.data_ingestion_service.config.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic electricityRawTopic(
            @Value("${app.kafka.topics.electricity-raw}") String topic,
            @Value("${app.kafka.partitions.electricity-raw:6}") int partitions,
            @Value("${app.kafka.replicas.electricity-raw:1}") int replicas
    ) {
        return TopicBuilder.name(topic)
                .partitions(partitions)
                .replicas(replicas)
                .build();
    }
}
