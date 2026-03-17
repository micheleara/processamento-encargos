package br.com.banco.processamento_encargos.adapter.output.producer;

import br.com.banco.processamento_encargos.adapter.output.producer.dto.LancamentoContabilEvent;
import br.com.banco.processamento_encargos.adapter.output.repository.LancamentoContabilPendenteJpaRepository;
import br.com.banco.processamento_encargos.adapter.output.repository.entity.LancamentoContabilPendenteEntity;
import br.com.banco.processamento_encargos.core.domain.model.ResultadoProcessamento;
import br.com.banco.processamento_encargos.port.output.PublicarLancamentoContabilOutputPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
public class LancamentoContabilProducer implements PublicarLancamentoContabilOutputPort {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final LancamentoContabilPendenteJpaRepository pendenteRepository;
    private final Counter contadorPublicados;
    private final Counter contadorOutbox;

    @Value("${encargos.kafka.topics.lancamento-contabil}")
    private String topicoLancamentoContabil;

    public LancamentoContabilProducer(KafkaTemplate<String, String> kafkaTemplate,
                                      ObjectMapper objectMapper,
                                      LancamentoContabilPendenteJpaRepository pendenteRepository,
                                      MeterRegistry meterRegistry) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.pendenteRepository = pendenteRepository;
        this.contadorPublicados = Counter.builder("encargos.lancamento.contabil.publicados")
                .description("Lançamentos contábeis publicados com sucesso no Kafka")
                .register(meterRegistry);
        this.contadorOutbox = Counter.builder("encargos.lancamento.contabil.outbox")
                .description("Lançamentos contábeis enviados para outbox por falha no Kafka")
                .register(meterRegistry);
    }

    @Override
    public void publicar(ResultadoProcessamento resultado) {
        LancamentoContabilEvent evento = new LancamentoContabilEvent(
                resultado.idLancamento(),
                resultado.numeroConta(),
                resultado.valor(),
                resultado.descricao(),
                resultado.saldoAnterior(),
                resultado.saldoPosterior()
        );

        String payload;
        try {
            payload = objectMapper.writeValueAsString(evento);
        } catch (Exception e) {
            log.error("Falha ao serializar lançamento contábil: idLancamento={}", resultado.idLancamento(), e);
            return;
        }

        try {
            kafkaTemplate.send(topicoLancamentoContabil, resultado.idLancamento(), payload);
            contadorPublicados.increment();
            log.debug("Lançamento contábil publicado: idLancamento={} conta={} valor={}",
                    resultado.idLancamento(), resultado.numeroConta(), resultado.valor());
        } catch (Exception e) {
            log.error("Falha ao publicar lançamento contábil — persistindo em outbox para retry: idLancamento={}",
                    resultado.idLancamento(), e);
            persistirOutbox(resultado.idLancamento(), payload);
        }
    }

    private void persistirOutbox(String idLancamento, String payload) {
        if (pendenteRepository.existsByIdLancamento(idLancamento)) {
            log.warn("Evento contábil já existe no outbox: idLancamento={}", idLancamento);
            return;
        }
        try {
            LancamentoContabilPendenteEntity pendente = LancamentoContabilPendenteEntity.builder()
                    .idLancamento(idLancamento)
                    .payload(payload)
                    .tentativas(1)
                    .criadoEm(LocalDateTime.now())
                    .ultimaTentativa(LocalDateTime.now())
                    .build();
            pendenteRepository.save(pendente);
            contadorOutbox.increment();
            log.info("Evento contábil persistido em outbox: idLancamento={}", idLancamento);
        } catch (Exception ex) {
            log.error("Falha crítica: não foi possível persistir evento no outbox: idLancamento={}", idLancamento, ex);
        }
    }
}