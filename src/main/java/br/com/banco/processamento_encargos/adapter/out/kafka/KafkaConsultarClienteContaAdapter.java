package br.com.banco.processamento_encargos.adapter.out.kafka;

import br.com.banco.processamento_encargos.adapter.out.kafka.dto.ConsultaContaRequestEvent;
import br.com.banco.processamento_encargos.adapter.out.kafka.dto.ConsultaContaResponseEvent;
import br.com.banco.processamento_encargos.domain.model.ContaInfo;
import br.com.banco.processamento_encargos.domain.model.StatusConta;
import br.com.banco.processamento_encargos.domain.port.out.ConsultarClienteContaPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
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
@RequiredArgsConstructor
public class KafkaConsultarClienteContaAdapter implements ConsultarClienteContaPort {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${encargos.kafka.topics.consulta-request}")
    private String topicoRequest;

    @Value("${encargos.kafka.consulta-timeout-seconds:5}")
    private long timeoutSeconds;

    private final ConcurrentHashMap<String, CompletableFuture<ContaInfo>> pendingRequests =
            new ConcurrentHashMap<>();


    @Override
    public ContaInfo consultarCliente(String numeroConta, String correlationId) {
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
            return ContaInfo.indisponivel(numeroConta);
        }

        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);

        } catch (TimeoutException e) {
            pendingRequests.remove(correlationId);
            log.warn("Timeout aguardando resposta do sistema de contas: numeroConta={} correlationId={}",
                    numeroConta, correlationId);
            return ContaInfo.indisponivel(numeroConta);

        } catch (Exception e) {
            pendingRequests.remove(correlationId);
            log.error("Erro ao aguardar resposta do sistema de contas: numeroConta={} correlationId={}",
                    numeroConta, correlationId, e);
            return ContaInfo.indisponivel(numeroConta);
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