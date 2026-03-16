package br.com.banco.processamento_encargos.adapter.out.kafka;

import br.com.banco.processamento_encargos.adapter.out.kafka.dto.AtualizarSaldoEvent;
import br.com.banco.processamento_encargos.domain.model.TipoLancamento;
import br.com.banco.processamento_encargos.domain.port.out.AtualizarSaldoContaPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaAtualizarSaldoContaAdapter implements AtualizarSaldoContaPort {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${encargos.kafka.topics.atualizar-saldo}")
    private String topicoAtualizarSaldo;

    private final ConcurrentHashMap<String, AtualizarSaldoEvent> pendentes = new ConcurrentHashMap<>();

    @Override
    public void publicarAtualizacaoSaldo(String idLancamento, String numeroConta, TipoLancamento tipo, BigDecimal valor) {
        AtualizarSaldoEvent evento = new AtualizarSaldoEvent(idLancamento, numeroConta, tipo.name(), valor);
        pendentes.put(idLancamento, evento);
        log.debug("Atualização de saldo registrada como pendente: idLancamento={} conta={} tipo={} valor={}",
                idLancamento, numeroConta, tipo, valor);
    }

    public void publicarSaldo(String idLancamento) {
        AtualizarSaldoEvent evento = pendentes.remove(idLancamento);
        if (evento == null) {
            log.warn("Nenhuma atualização de saldo pendente para idLancamento={} — ignorando", idLancamento);
            return;
        }

        try {
            String payload = objectMapper.writeValueAsString(evento);
            kafkaTemplate.send(topicoAtualizarSaldo, evento.numeroConta(), payload);
            log.debug("Atualização de saldo publicada: idLancamento={} conta={} tipo={} valor={}",
                    idLancamento, evento.numeroConta(), evento.tipoLancamento(), evento.valor());
        } catch (Exception e) {
            log.error("Erro ao publicar atualização de saldo: idLancamento={}", idLancamento, e);
        }
    }
}