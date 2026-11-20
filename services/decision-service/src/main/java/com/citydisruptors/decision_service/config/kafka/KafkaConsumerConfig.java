package com.citydisruptors.decision_service.config.kafka;

import com.citydisruptors.decision_service.config.kafka.events.PredictionGeneratedEvent;
import com.citydisruptors.decision_service.config.kafka.events.TrafficPredictionGeneratedEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConsumerConfig {

    @Bean
    public ConsumerFactory<String, PredictionGeneratedEvent> predictionConsumerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
            @Value("${spring.kafka.consumer.group-id}") String groupId
    ) {
        JsonDeserializer<PredictionGeneratedEvent> jsonDeserializer =
                new JsonDeserializer<>(PredictionGeneratedEvent.class);

        jsonDeserializer.addTrustedPackages("*");
        jsonDeserializer.setUseTypeHeaders(false);

        Map<String, Object> props = new HashMap<>();

        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        return new DefaultKafkaConsumerFactory<>(
                props,
                new StringDeserializer(),
                jsonDeserializer
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PredictionGeneratedEvent>
    predictionKafkaListenerContainerFactory(
            ConsumerFactory<String, PredictionGeneratedEvent> predictionConsumerFactory
    ) {
        ConcurrentKafkaListenerContainerFactory<String, PredictionGeneratedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(predictionConsumerFactory);
        factory.setConcurrency(3);

        return factory;
    }

    /**
     * Traffic predictions come from the Python AI service. A record that cannot
     * be parsed is handed to the error handler and skipped instead of being
     * polled again forever.
     */
    @Bean
    public ConsumerFactory<String, TrafficPredictionGeneratedEvent> trafficPredictionConsumerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
            @Value("${spring.kafka.consumer.group-id}") String groupId
    ) {
        JsonDeserializer<TrafficPredictionGeneratedEvent> jsonDeserializer =
                new JsonDeserializer<>(TrafficPredictionGeneratedEvent.class);

        jsonDeserializer.addTrustedPackages(TrafficPredictionGeneratedEvent.class.getPackageName());
        jsonDeserializer.setUseTypeHeaders(false);

        Map<String, Object> props = new HashMap<>();

        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId + "-traffic");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        return new DefaultKafkaConsumerFactory<>(
                props,
                new StringDeserializer(),
                new ErrorHandlingDeserializer<>(jsonDeserializer)
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, TrafficPredictionGeneratedEvent>
    trafficPredictionKafkaListenerContainerFactory(
            ConsumerFactory<String, TrafficPredictionGeneratedEvent> trafficPredictionConsumerFactory
    ) {
        ConcurrentKafkaListenerContainerFactory<String, TrafficPredictionGeneratedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(trafficPredictionConsumerFactory);
        factory.setCommonErrorHandler(new DefaultErrorHandler(new FixedBackOff(1000L, 5)));

        return factory;
    }
}
