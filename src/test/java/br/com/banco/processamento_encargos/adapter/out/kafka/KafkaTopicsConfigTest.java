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
        setField(kafkaTopicsConfig, "topicoLancamentoContabil", "encargos.lancamento-contabil");
        setField(kafkaTopicsConfig, "topicoConfirmacaoContabil", "encargos.contabil-confirmacao");
        setField(kafkaTopicsConfig, "topicoAtualizarSaldo", "encargos.conta.atualizar-saldo");
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

    @Test
    @DisplayName("Deve criar tópico de lançamento contábil com configurações corretas")
    void deveCriarTopicLancamentoContabilComConfiguracoesCorretas() {
        NewTopic topic = kafkaTopicsConfig.topicLancamentoContabil();

        assertNotNull(topic);
        assertEquals("encargos.lancamento-contabil", topic.name());
        assertEquals(10, topic.numPartitions());
        assertEquals(1, topic.replicationFactor());
    }

    @Test
    @DisplayName("Deve criar tópico de confirmação contábil com configurações corretas")
    void deveCriarTopicConfirmacaoContabilComConfiguracoesCorretas() {
        NewTopic topic = kafkaTopicsConfig.topicConfirmacaoContabil();

        assertNotNull(topic);
        assertEquals("encargos.contabil-confirmacao", topic.name());
        assertEquals(10, topic.numPartitions());
        assertEquals(1, topic.replicationFactor());
    }

    @Test
    @DisplayName("Deve criar tópico de atualizar saldo com configurações corretas")
    void deveCriarTopicAtualizarSaldoComConfiguracoesCorretas() {
        NewTopic topic = kafkaTopicsConfig.topicAtualizarSaldo();

        assertNotNull(topic);
        assertEquals("encargos.conta.atualizar-saldo", topic.name());
        assertEquals(10, topic.numPartitions());
        assertEquals(1, topic.replicationFactor());
    }

    @Test
    @DisplayName("Todos os cinco tópicos devem ter nomes distintos")
    void todosCincoTopicosDevemTerNomesDistintos() {
        NewTopic t1 = kafkaTopicsConfig.topicConsultaRequest();
        NewTopic t2 = kafkaTopicsConfig.topicConsultaResponse();
        NewTopic t3 = kafkaTopicsConfig.topicLancamentoContabil();
        NewTopic t4 = kafkaTopicsConfig.topicConfirmacaoContabil();
        NewTopic t5 = kafkaTopicsConfig.topicAtualizarSaldo();

        long nomes = java.util.Set.of(t1.name(), t2.name(), t3.name(), t4.name(), t5.name()).size();
        assertEquals(5, nomes);
    }
}