package br.com.banco.processamento_encargos.adapter.out.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class KafkaTopicsConfigTest {

    private KafkaTopicsConfig kafkaTopicsConfig;

    @BeforeEach
    void setUp() throws Exception {
        kafkaTopicsConfig = new KafkaTopicsConfig();
        setField(kafkaTopicsConfig, "topicoRequest", "encargos.conta.consulta.request");
        setField(kafkaTopicsConfig, "topicoResponse", "encargos.conta.consulta.response");
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    @DisplayName("Deve criar tópico de request com nome, partições e réplicas corretos")
    void deveCriarTopicConsultaRequestComConfiguracoesCorretas() {
        NewTopic topic = kafkaTopicsConfig.topicConsultaRequest();

        assertNotNull(topic);
        assertEquals("encargos.conta.consulta.request", topic.name());
        assertEquals(10, topic.numPartitions());
        assertEquals(1, topic.replicationFactor());
    }

    @Test
    @DisplayName("Deve criar tópico de response com nome, partições e réplicas corretos")
    void deveCriarTopicConsultaResponseComConfiguracoesCorretas() {
        NewTopic topic = kafkaTopicsConfig.topicConsultaResponse();

        assertNotNull(topic);
        assertEquals("encargos.conta.consulta.response", topic.name());
        assertEquals(10, topic.numPartitions());
        assertEquals(1, topic.replicationFactor());
    }

    @Test
    @DisplayName("Os dois tópicos devem ter nomes distintos")
    void osTopicosDevemTerNomesDistintos() {
        NewTopic request = kafkaTopicsConfig.topicConsultaRequest();
        NewTopic response = kafkaTopicsConfig.topicConsultaResponse();

        assertNotEquals(request.name(), response.name());
    }

    @Test
    @DisplayName("Os dois tópicos devem ter a mesma quantidade de partições")
    void osTopicosDevemTerMesmaQuantidadeDeParticoes() {
        NewTopic request = kafkaTopicsConfig.topicConsultaRequest();
        NewTopic response = kafkaTopicsConfig.topicConsultaResponse();

        assertEquals(request.numPartitions(), response.numPartitions());
    }

    @Test
    @DisplayName("Os dois tópicos devem ter o mesmo fator de replicação")
    void osTopicosDevemTerMesmoFatorDeReplicacao() {
        NewTopic request = kafkaTopicsConfig.topicConsultaRequest();
        NewTopic response = kafkaTopicsConfig.topicConsultaResponse();

        assertEquals(request.replicationFactor(), response.replicationFactor());
    }
}