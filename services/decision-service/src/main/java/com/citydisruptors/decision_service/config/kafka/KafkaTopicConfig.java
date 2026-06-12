package com.citydisruptors.decision_service.config.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic predictionsGeneratedTopic(
            @Value("${app.kafka.topics.predictions-generated}") String topic,
            @Value("${app.kafka.partitions.predictions-generated:6}") int partitions,
            @Value("${app.kafka.replicas.predictions-generated:1}") int replicas
    ) {
        return TopicBuilder.name(topic)
                .partitions(partitions)
                .replicas(replicas)
                .build();
    }

    @Bean
    public NewTopic alertsCreatedTopic(
            @Value("${app.kafka.topics.alerts-created}") String topic,
            @Value("${app.kafka.partitions.alerts-created:6}") int partitions,
            @Value("${app.kafka.replicas.alerts-created:1}") int replicas
    ) {
        return TopicBuilder.name(topic)
                .partitions(partitions)
                .replicas(replicas)
                .build();
    }
}
