package br.com.banco.processamento_encargos.adapter.out.stub;

import br.com.banco.processamento_encargos.domain.model.ResultadoProcessamento;
import br.com.banco.processamento_encargos.domain.port.out.PersistirResultadoPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class StubPersistirResultadoAdapter implements PersistirResultadoPort {

    @Override
    public void persistir(ResultadoProcessamento resultado) {
        log.debug("[STUB] Persistindo resultado: id={} status={} motivo={}",
                resultado.idLancamento(), resultado.status(), resultado.motivoRejeicao());
    }
}

