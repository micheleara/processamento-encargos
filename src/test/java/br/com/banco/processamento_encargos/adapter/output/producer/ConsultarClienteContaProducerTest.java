package br.com.banco.processamento_encargos.adapter.output.producer;

import br.com.banco.processamento_encargos.adapter.output.producer.dto.ConsultaContaResponseEvent;
import br.com.banco.processamento_encargos.core.domain.model.ContaInfo;
import br.com.banco.processamento_encargos.core.domain.model.StatusConta;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
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
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConsultarClienteContaProducerTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private Acknowledgment ack;

    private ConsultarClienteContaProducer adapter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        adapter = new ConsultarClienteContaProducer(kafkaTemplate, objectMapper, CircuitBreakerRegistry.ofDefaults());
        setField(adapter, "topicoRequest", "encargos.conta.consulta.request");
        setField(adapter, "timeoutSeconds", 1L);
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
        ContaInfo resultado = adapter.consultarCliente("001234-5", "corr-timeout");

        assertEquals(StatusConta.INDISPONIVEL, resultado.status());
        assertEquals("001234-5", resultado.numeroConta());
    }

    @Test
    @DisplayName("consumirResposta deve publicar evento e verificar a chave S3")
    void devePublicarNaChaveCorreta() throws Exception {
        ArgumentCaptor<org.apache.kafka.clients.producer.ProducerRecord> captor =
                ArgumentCaptor.forClass(org.apache.kafka.clients.producer.ProducerRecord.class);

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
    @DisplayName("Requisições concorrentes devem receber respostas com correlationId correto sem cross-contamination")
    void deveCorrelacionarRespostasCorretamenteEmConcorrencia() throws Exception {
        int total = 10;
        ExecutorService executor = Executors.newFixedThreadPool(total);
        List<Future<ContaInfo>> futures = new ArrayList<>();

        for (int i = 0; i < total; i++) {
            String correlationId = "corr-conc-" + i;
            String numeroConta = "conta-" + i;
            String nome = "Cliente " + i;
            BigDecimal saldo = new BigDecimal(i * 100);

            futures.add(executor.submit(() -> {
                CompletableFuture.delayedExecutor(50, TimeUnit.MILLISECONDS).execute(() -> {
                    try {
                        ConsultaContaResponseEvent evt = new ConsultaContaResponseEvent(
                                correlationId, numeroConta, nome, "ATIVA", saldo);
                        ConsumerRecord<String, String> rec = new ConsumerRecord<>(
                                "encargos.conta.consulta.response", 0, 0L, numeroConta,
                                objectMapper.writeValueAsString(evt));
                        rec.headers().add("correlationId", correlationId.getBytes(StandardCharsets.UTF_8));
                        adapter.consumirResposta(rec, mock(Acknowledgment.class));
                    } catch (Exception ignored) {}
                });
                return adapter.consultarCliente(numeroConta, correlationId);
            }));
        }

        executor.shutdown();

        for (int i = 0; i < total; i++) {
            ContaInfo resultado = futures.get(i).get(3, TimeUnit.SECONDS);
            assertEquals(StatusConta.ATIVA, resultado.status());
            assertEquals("conta-" + i, resultado.numeroConta());
            assertEquals("Cliente " + i, resultado.nomeCliente());
        }
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

    @Test
    @DisplayName("Deve abrir circuit breaker após múltiplos timeouts consecutivos")
    void deveAbrirCircuitoAposMultiplosFalhas() throws Exception {
        // Circuit breaker configurado com janela pequena para este teste
        CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(3)
                .failureRateThreshold(100)
                .waitDurationInOpenState(Duration.ofSeconds(60))
                .build();
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(cbConfig);
        ConsultarClienteContaProducer adapterCb = new ConsultarClienteContaProducer(
                kafkaTemplate, objectMapper, registry);
        setField(adapterCb, "topicoRequest", "encargos.conta.consulta.request");
        setField(adapterCb, "timeoutSeconds", 1L);

        // 3 timeouts consecutivos → abre o circuito
        adapterCb.consultarCliente("001", "corr-fail-1");
        adapterCb.consultarCliente("002", "corr-fail-2");
        adapterCb.consultarCliente("003", "corr-fail-3");

        CircuitBreaker cb = registry.circuitBreaker("consultarCliente");
        assertEquals(CircuitBreaker.State.OPEN, cb.getState());
    }

    @Test
    @DisplayName("Deve retornar INDISPONIVEL imediatamente quando circuit breaker está aberto")
    void deveRetornarIndisponivelImediatamenteComCircuitoAberto() throws Exception {
        CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(2)
                .failureRateThreshold(100)
                .waitDurationInOpenState(Duration.ofSeconds(60))
                .build();
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(cbConfig);
        ConsultarClienteContaProducer adapterCb = new ConsultarClienteContaProducer(
                kafkaTemplate, objectMapper, registry);
        setField(adapterCb, "topicoRequest", "encargos.conta.consulta.request");
        setField(adapterCb, "timeoutSeconds", 1L);

        // Força abertura do circuito
        adapterCb.consultarCliente("001", "corr-open-1");
        adapterCb.consultarCliente("002", "corr-open-2");

        // Reset mock para contar apenas chamadas com circuito aberto
        clearInvocations(kafkaTemplate);

        long inicio = System.currentTimeMillis();
        ContaInfo resultado = adapterCb.consultarCliente("003", "corr-open-3");
        long duracao = System.currentTimeMillis() - inicio;

        assertEquals(StatusConta.INDISPONIVEL, resultado.status());
        // Retorno imediato (sem aguardar timeout de 1s)
        assertTrue(duracao < 500, "Circuit aberto deve retornar imediatamente, mas levou " + duracao + "ms");
        // Kafka não foi chamado — circuito impediu a requisição
        verifyNoInteractions(kafkaTemplate);
    }
}