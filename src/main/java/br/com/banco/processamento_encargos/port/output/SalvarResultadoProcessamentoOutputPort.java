package br.com.banco.processamento_encargos.port.output;

import br.com.banco.processamento_encargos.core.domain.model.ResultadoProcessamento;

public interface SalvarResultadoProcessamentoOutputPort {

    void salvar(ResultadoProcessamento resultado);
}