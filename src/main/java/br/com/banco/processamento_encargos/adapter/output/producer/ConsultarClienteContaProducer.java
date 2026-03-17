package br.com.banco.processamento_encargos.adapter.output.producer;

import br.com.banco.processamento_encargos.adapter.output.producer.dto.ConsultaContaRequestEvent;
import br.com.banco.processamento_encargos.adapter.output.producer.dto.ConsultaContaResponseEvent;
import br.com.banco.processamento_encargos.core.domain.model.ContaInfo;
import br.com.banco.processamento_encargos.core.domain.model.StatusConta;
import br.com.banco.processamento_encargos.port.output.ConsultarClienteContaOutputPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
public class ConsultarClienteContaProducer implements ConsultarClienteContaOutputPort {

    private static final String CIRCUIT_BREAKER_NAME = "consultarCliente";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final CircuitBreaker circuitBreaker;

    @Value("${encargos.kafka.topics.consulta-request}")
    private String topicoRequest;

    @Value("${encargos.kafka.consulta-timeout-seconds:5}")
    private long timeoutSeconds;

    private final ConcurrentHashMap<String, CompletableFuture<ContaInfo>> pendingRequests =
            new ConcurrentHashMap<>();

    public ConsultarClienteContaProducer(KafkaTemplate<String, String> kafkaTemplate,
                                         ObjectMapper objectMapper,
                                         CircuitBreakerRegistry circuitBreakerRegistry) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker(CIRCUIT_BREAKER_NAME);
    }

    @Override
    public ContaInfo consultarCliente(String numeroConta, String correlationId) {
        try {
            return circuitBreaker.executeCheckedSupplier(
                    () -> executarConsulta(numeroConta, correlationId));

        } catch (CallNotPermittedException e) {
            log.warn("Circuit breaker aberto — sistema de contas indisponível: numeroConta={}", numeroConta);
            return ContaInfo.indisponivel(numeroConta);

        } catch (Throwable e) {
            log.error("Erro na consulta de conta: numeroConta={} correlationId={}", numeroConta, correlationId, e);
            return ContaInfo.indisponivel(numeroConta);
        }
    }

    private ContaInfo executarConsulta(String numeroConta, String correlationId) throws Exception {
        CompletableFuture<ContaInfo> future = new CompletableFuture<>();
        // Registra ANTES de publicar para evitar race condition com resposta imediata
        pendingRequests.put(correlationId, future);

        try {
            ConsultaContaRequestEvent evento = new ConsultaContaRequestEvent(correlationId, numeroConta);
            String payload = objectMapper.writeValueAsString(evento);

            ProducerRecord<String, String> record = new ProducerRecord<>(topicoRequest, numeroConta, payload);
            record.headers().add("correlationId", correlationId.getBytes(StandardCharsets.UTF_8));
            kafkaTemplate.send(record);

            log.debug("Consulta publicada: numeroConta={} correlationId={}", numeroConta, correlationId);

        } catch (Exception e) {
            pendingRequests.remove(correlationId);
            log.error("Erro ao publicar consulta de conta: numeroConta={} correlationId={}", numeroConta, correlationId, e);
            throw e;
        }

        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);

        } catch (TimeoutException e) {
            pendingRequests.remove(correlationId);
            log.warn("Timeout aguardando resposta do sistema de contas: numeroConta={} correlationId={}",
                    numeroConta, correlationId);
            throw e;

        } catch (Exception e) {
            pendingRequests.remove(correlationId);
            log.error("Erro ao aguardar resposta do sistema de contas: numeroConta={} correlationId={}",
                    numeroConta, correlationId, e);
            throw e;
        }
    }

    @KafkaListener(
            topics = "${encargos.kafka.topics.consulta-response}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consumirResposta(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            Header correlationHeader = record.headers().lastHeader("correlationId");
            if (correlationHeader == null) {
                log.warn("Resposta recebida sem header correlationId — ignorando. offset={}", record.offset());
                return;
            }

            String correlationId = new String(correlationHeader.value(), StandardCharsets.UTF_8);
            ConsultaContaResponseEvent evento = objectMapper.readValue(record.value(), ConsultaContaResponseEvent.class);

            ContaInfo contaInfo = new ContaInfo(
                    evento.numeroConta(),
                    evento.nomeCliente(),
                    StatusConta.valueOf(evento.status()),
                    evento.saldo()
            );

            CompletableFuture<ContaInfo> future = pendingRequests.remove(correlationId);
            if (future != null) {
                future.complete(contaInfo);
                log.debug("Resposta processada: numeroConta={} status={} correlationId={}",
                        evento.numeroConta(), evento.status(), correlationId);
            } else {
                log.warn("Nenhuma requisição pendente para correlationId={} — resposta ignorada", correlationId);
            }

        } catch (Exception e) {
            log.error("Erro ao processar resposta do sistema de contas: offset={}", record.offset(), e);
        } finally {
            ack.acknowledge();
        }
    }
}