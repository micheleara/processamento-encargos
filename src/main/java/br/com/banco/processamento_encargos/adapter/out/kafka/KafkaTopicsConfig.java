package br.com.banco.processamento_encargos.adapter.out.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicsConfig {

    @Value("${encargos.kafka.topics.consulta-request}")
    private String topicoRequest;

    @Value("${encargos.kafka.topics.consulta-response}")
    private String topicoResponse;

    @Bean
    public NewTopic topicConsultaRequest() {
        return TopicBuilder.name(topicoRequest)
                .partitions(10)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic topicConsultaResponse() {
        return TopicBuilder.name(topicoResponse)
                .partitions(10)
                .replicas(1)
                .build();
    }
}