package com.citydisruptors.incidentservice.config.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic incidentsCreatedTopic(
            @Value("${app.kafka.topics.incidents-created}") String topic,
            @Value("${app.kafka.partitions.incidents-created:6}") int partitions,
            @Value("${app.kafka.replicas.incidents-created:1}") int replicas
    ) {
        return TopicBuilder.name(topic)
                .partitions(partitions)
                .replicas(replicas)
                .build();
    }

    @Bean
    public NewTopic incidentsUpdatedTopic(
            @Value("${app.kafka.topics.incidents-updated}") String topic,
            @Value("${app.kafka.partitions.incidents-updated:6}") int partitions,
            @Value("${app.kafka.replicas.incidents-updated:1}") int replicas
    ) {
        return TopicBuilder.name(topic)
                .partitions(partitions)
                .replicas(replicas)
                .build();
    }
}
