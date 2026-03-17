package br.com.banco.processamento_encargos.adapter.output.producer;

import br.com.banco.processamento_encargos.adapter.output.repository.LancamentoContabilPendenteJpaRepository;
import br.com.banco.processamento_encargos.adapter.output.repository.entity.LancamentoContabilPendenteEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class LancamentoContabilPendenteScheduler {

    private final LancamentoContabilPendenteJpaRepository pendenteRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${encargos.kafka.topics.lancamento-contabil}")
    private String topicoLancamentoContabil;

    @Value("${encargos.outbox.max-tentativas:5}")
    private int maxTentativas;

    @Scheduled(fixedDelayString = "${encargos.outbox.retry-interval-ms:30000}")
    @Transactional
    public void reprocessarPendentes() {
        List<LancamentoContabilPendenteEntity> pendentes = pendenteRepository.findAllByOrderByCriadoEmAsc();
        if (pendentes.isEmpty()) {
            return;
        }

        log.info("Outbox: {} evento(s) contábil(is) pendente(s) para retry", pendentes.size());

        for (LancamentoContabilPendenteEntity pendente : pendentes) {
            if (pendente.getTentativas() >= maxTentativas) {
                log.error("Outbox: evento contábil atingiu limite de tentativas — descartando: idLancamento={} tentativas={}",
                        pendente.getIdLancamento(), pendente.getTentativas());
                pendenteRepository.delete(pendente);
                continue;
            }

            try {
                kafkaTemplate.send(topicoLancamentoContabil, pendente.getIdLancamento(), pendente.getPayload());
                pendenteRepository.delete(pendente);
                log.info("Outbox: evento contábil re-publicado com sucesso: idLancamento={} tentativas={}",
                        pendente.getIdLancamento(), pendente.getTentativas());
            } catch (Exception e) {
                pendente.setTentativas(pendente.getTentativas() + 1);
                pendente.setUltimaTentativa(LocalDateTime.now());
                pendenteRepository.save(pendente);
                log.warn("Outbox: falha no retry de evento contábil: idLancamento={} tentativas={}",
                        pendente.getIdLancamento(), pendente.getTentativas(), e);
            }
        }
    }
}