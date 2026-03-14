package br.com.banco.processamento_encargos.domain.port.in;

import br.com.banco.processamento_encargos.domain.model.Lancamento;
import br.com.banco.processamento_encargos.domain.model.ResultadoProcessamento;

public interface ProcessarLancamentoPort {

    ResultadoProcessamento processar(Lancamento lancamento);
}

