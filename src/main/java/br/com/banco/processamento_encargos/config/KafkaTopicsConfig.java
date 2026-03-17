package br.com.banco.processamento_encargos.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicsConfig {

    @Value("${encargos.kafka.topics.consulta-request}")
    private String topicoRequest;

    @Value("${encargos.kafka.topics.consulta-response}")
    private String topicoResponse;

    @Value("${encargos.kafka.topics.lancamento-contabil}")
    private String topicoLancamentoContabil;

    @Value("${encargos.kafka.topics.confirmacao-contabil}")
    private String topicoConfirmacaoContabil;

    @Value("${encargos.kafka.topics.atualizar-saldo}")
    private String topicoAtualizarSaldo;

    @Value("${encargos.kafka.topics.confirmacao-contabil-dlt}")
    private String topicoConfirmacaoContabilDlt;

    @Value("${encargos.kafka.topics.consulta-response-dlt}")
    private String topicoConsultaResponseDlt;

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

    @Bean
    public NewTopic topicLancamentoContabil() {
        return TopicBuilder.name(topicoLancamentoContabil)
                .partitions(10)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic topicConfirmacaoContabil() {
        return TopicBuilder.name(topicoConfirmacaoContabil)
                .partitions(10)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic topicAtualizarSaldo() {
        return TopicBuilder.name(topicoAtualizarSaldo)
                .partitions(10)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic topicConfirmacaoContabilDlt() {
        return TopicBuilder.name(topicoConfirmacaoContabilDlt)
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic topicConsultaResponseDlt() {
        return TopicBuilder.name(topicoConsultaResponseDlt)
                .partitions(1)
                .replicas(1)
                .build();
    }
}