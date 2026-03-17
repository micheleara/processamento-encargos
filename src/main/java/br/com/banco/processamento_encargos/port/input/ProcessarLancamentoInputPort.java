package br.com.banco.processamento_encargos.port.input;

import br.com.banco.processamento_encargos.core.domain.model.Lancamento;
import br.com.banco.processamento_encargos.core.domain.model.ResultadoProcessamento;

public interface ProcessarLancamentoInputPort {

    ResultadoProcessamento processar(Lancamento lancamento);
}
