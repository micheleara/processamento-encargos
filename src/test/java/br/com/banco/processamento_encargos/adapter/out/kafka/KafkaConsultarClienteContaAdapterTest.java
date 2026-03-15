package br.com.banco.processamento_encargos.adapter.out.kafka;

import br.com.banco.processamento_encargos.adapter.out.kafka.dto.ConsultaContaResponseEvent;
import br.com.banco.processamento_encargos.domain.model.ContaInfo;
import br.com.banco.processamento_encargos.domain.model.StatusConta;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KafkaConsultarClienteContaAdapterTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private Acknowledgment ack;

    private KafkaConsultarClienteContaAdapter adapter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        adapter = new KafkaConsultarClienteContaAdapter(kafkaTemplate, objectMapper);
        setField(adapter, "topicoRequest", "encargos.conta.consulta.request");
        setField(adapter, "timeoutSeconds", 1L); // timeout curto para testes rápidos
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    @DisplayName("Deve publicar evento e retornar ContaInfo quando resposta chegar antes do timeout")
    void deveRetornarContaInfoQuandoRespostaChegar() throws Exception {
        ConsultaContaResponseEvent responseEvent = new ConsultaContaResponseEvent(
                "corr-123", "001234-5", "João da Silva", "ATIVA", new BigDecimal("5000.00"));
        String responseJson = objectMapper.writeValueAsString(responseEvent);

        // Simula resposta chegando 100ms após a publicação
        CompletableFuture.delayedExecutor(100, TimeUnit.MILLISECONDS).execute(() -> {
            ConsumerRecord<String, String> record = new ConsumerRecord<>(
                    "encargos.conta.consulta.response", 0, 0L, "001234-5", responseJson);
            record.headers().add("correlationId", "corr-123".getBytes(StandardCharsets.UTF_8));
            adapter.consumirResposta(record, ack);
        });

        ContaInfo resultado = adapter.consultarCliente("001234-5", "corr-123");

        assertEquals("001234-5", resultado.numeroConta());
        assertEquals("João da Silva", resultado.nomeCliente());
        assertEquals(StatusConta.ATIVA, resultado.status());
        assertEquals(new BigDecimal("5000.00"), resultado.saldo());
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("Deve retornar ContaInfo.indisponivel quando timeout expirar")
    void deveRetornarIndisponivelQuandoTimeout() {
        // Não simula resposta — deixa expirar o timeout de 1 segundo
        ContaInfo resultado = adapter.consultarCliente("001234-5", "corr-timeout");

        assertEquals(StatusConta.INDISPONIVEL, resultado.status());
        assertEquals("001234-5", resultado.numeroConta());
    }

    @Test
    @DisplayName("consumirResposta deve publicar evento e verificar a chave S3")
    void devePublicarNaChaveCorreta() throws Exception {
        ArgumentCaptor<org.apache.kafka.clients.producer.ProducerRecord> captor =
                ArgumentCaptor.forClass(org.apache.kafka.clients.producer.ProducerRecord.class);

        // Simula resposta para não bloquear o teste
        CompletableFuture.delayedExecutor(50, TimeUnit.MILLISECONDS).execute(() -> {
            try {
                ConsultaContaResponseEvent evt = new ConsultaContaResponseEvent(
                        "corr-key", "9999-0", "Maria", "ATIVA", BigDecimal.TEN);
                ConsumerRecord<String, String> rec = new ConsumerRecord<>(
                        "encargos.conta.consulta.response", 0, 0L, "9999-0",
                        objectMapper.writeValueAsString(evt));
                rec.headers().add("correlationId", "corr-key".getBytes(StandardCharsets.UTF_8));
                adapter.consumirResposta(rec, ack);
            } catch (Exception ignored) {}
        });

        adapter.consultarCliente("9999-0", "corr-key");

        verify(kafkaTemplate).send(captor.capture());
        assertEquals("encargos.conta.consulta.request", captor.getValue().topic());
        assertEquals("9999-0", captor.getValue().key());
    }

    @Test
    @DisplayName("consumirResposta deve ignorar mensagem sem header correlationId")
    void consumirRespostaDeveIgnorarSemCorrelationId() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "encargos.conta.consulta.response", 0, 0L, null, "{}");
        // Sem header correlationId

        assertDoesNotThrow(() -> adapter.consumirResposta(record, ack));
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("consumirResposta deve ignorar correlationId sem requisição pendente")
    void consumirRespostaDeveIgnorarCorrelationIdDesconhecido() throws Exception {
        ConsultaContaResponseEvent evento = new ConsultaContaResponseEvent(
                "corr-desconhecido", "001234-5", "Fulano", "ATIVA", BigDecimal.TEN);
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "encargos.conta.consulta.response", 0, 0L, "001234-5",
                objectMapper.writeValueAsString(evento));
        record.headers().add("correlationId", "corr-desconhecido".getBytes(StandardCharsets.UTF_8));

        assertDoesNotThrow(() -> adapter.consumirResposta(record, ack));
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("consumirResposta deve retornar INDISPONIVEL quando status for INDISPONIVEL")
    void deveMapearStatusIndisponivel() throws Exception {
        ConsultaContaResponseEvent responseEvent = new ConsultaContaResponseEvent(
                "corr-indisp", "005555-9", "Pedro", "INDISPONIVEL", BigDecimal.ZERO);
        String responseJson = objectMapper.writeValueAsString(responseEvent);

        CompletableFuture.delayedExecutor(50, TimeUnit.MILLISECONDS).execute(() -> {
            ConsumerRecord<String, String> record = new ConsumerRecord<>(
                    "encargos.conta.consulta.response", 0, 0L, "005555-9", responseJson);
            record.headers().add("correlationId", "corr-indisp".getBytes(StandardCharsets.UTF_8));
            adapter.consumirResposta(record, ack);
        });

        ContaInfo resultado = adapter.consultarCliente("005555-9", "corr-indisp");

        assertEquals(StatusConta.INDISPONIVEL, resultado.status());
    }
}