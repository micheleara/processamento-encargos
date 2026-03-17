package br.com.banco.processamento_encargos.adapter.out.kafka;

import br.com.banco.processamento_encargos.adapter.out.kafka.dto.LancamentoContabilEvent;
import br.com.banco.processamento_encargos.domain.model.ResultadoProcessamento;
import br.com.banco.processamento_encargos.domain.port.out.PublicarLancamentoContabilPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaLancamentoContabilAdapter implements PublicarLancamentoContabilPort {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${encargos.kafka.topics.lancamento-contabil}")
    private String topicoLancamentoContabil;

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

        try {
            String payload = objectMapper.writeValueAsString(evento);
            kafkaTemplate.send(topicoLancamentoContabil, resultado.idLancamento(), payload);
            log.debug("Lançamento contábil publicado: idLancamento={} conta={} valor={}",
                    resultado.idLancamento(), resultado.numeroConta(), resultado.valor());
        } catch (Exception e) {
            log.error("Falha ao publicar lançamento contábil — registro já persistido, evento Kafka perdido: idLancamento={}", resultado.idLancamento(), e);
        }
    }
}