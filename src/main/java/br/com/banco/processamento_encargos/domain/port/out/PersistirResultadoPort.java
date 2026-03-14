package br.com.banco.processamento_encargos.domain.port.out;

import br.com.banco.processamento_encargos.domain.model.ResultadoProcessamento;

public interface PersistirResultadoPort {

    void persistir(ResultadoProcessamento resultado);
}

