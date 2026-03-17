package br.com.banco.processamento_encargos.adapter.output.producer;

import br.com.banco.processamento_encargos.adapter.output.producer.dto.AtualizarSaldoEvent;
import br.com.banco.processamento_encargos.adapter.output.repository.SaldoPendenteJpaRepository;
import br.com.banco.processamento_encargos.adapter.output.repository.entity.SaldoPendenteEntity;
import br.com.banco.processamento_encargos.core.domain.model.TipoLancamento;
import br.com.banco.processamento_encargos.port.output.AtualizarSaldoContaOutputPort;
import br.com.banco.processamento_encargos.port.output.PublicarAtualizacaoSaldoOutputPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class AtualizarSaldoContaProducer implements AtualizarSaldoContaOutputPort, PublicarAtualizacaoSaldoOutputPort {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final SaldoPendenteJpaRepository saldoPendenteRepository;

    @Value("${encargos.kafka.topics.atualizar-saldo}")
    private String topicoAtualizarSaldo;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publicarAtualizacaoSaldo(String idLancamento, String numeroConta, TipoLancamento tipo, BigDecimal valor) {
        if (saldoPendenteRepository.existsByIdLancamento(idLancamento)) {
            log.warn("Saldo pendente já registrado para idLancamento={} — ignorando duplicata", idLancamento);
            return;
        }
        SaldoPendenteEntity entity = SaldoPendenteEntity.builder()
                .idLancamento(idLancamento)
                .numConta(numeroConta)
                .tipoLancamento(tipo.name())
                .valor(valor)
                .criadoEm(LocalDateTime.now())
                .build();
        saldoPendenteRepository.save(entity);
        log.debug("Saldo pendente persistido: idLancamento={} conta={} tipo={} valor={}",
                idLancamento, numeroConta, tipo, valor);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publicarSaldo(String idLancamento) {
        SaldoPendenteEntity entity = saldoPendenteRepository.findByIdLancamento(idLancamento).orElse(null);
        if (entity == null) {
            log.warn("Nenhum saldo pendente para idLancamento={} — ignorando", idLancamento);
            return;
        }

        try {
            AtualizarSaldoEvent evento = new AtualizarSaldoEvent(
                    entity.getIdLancamento(), entity.getNumConta(), entity.getTipoLancamento(), entity.getValor());
            String payload = objectMapper.writeValueAsString(evento);
            kafkaTemplate.send(topicoAtualizarSaldo, entity.getNumConta(), payload);
            saldoPendenteRepository.delete(entity);
            log.debug("Saldo publicado e removido da fila: idLancamento={} conta={}",
                    idLancamento, entity.getNumConta());
        } catch (Exception e) {
            log.error("Erro ao publicar atualização de saldo: idLancamento={}", idLancamento, e);
        }
    }
}