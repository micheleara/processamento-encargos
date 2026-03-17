package br.com.banco.processamento_encargos.adapter.input.consumer;

import br.com.banco.processamento_encargos.adapter.output.producer.dto.ConfirmacaoContabilEvent;
import br.com.banco.processamento_encargos.port.output.PublicarAtualizacaoSaldoOutputPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaConfirmacaoContabilConsumer {

    private final PublicarAtualizacaoSaldoOutputPort publicarAtualizacaoSaldoPort;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "${encargos.kafka.topics.confirmacao-contabil}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consumirConfirmacao(
            String payload,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            Acknowledgment ack) throws Exception {

        ConfirmacaoContabilEvent evento = objectMapper.readValue(payload, ConfirmacaoContabilEvent.class);
        log.debug("Confirmação contábil recebida: topic={} idLancamento={} status={}",
                topic, evento.idLancamento(), evento.status());

        if ("PROCESSADO".equals(evento.status())) {
            publicarAtualizacaoSaldoPort.publicarSaldo(evento.idLancamento());
            log.info("Saldo publicado após confirmação contábil: idLancamento={}", evento.idLancamento());
        } else {
            log.info("Confirmação contábil com status={} — saldo não publicado: idLancamento={}",
                    evento.status(), evento.idLancamento());
        }

        ack.acknowledge();
    }
}