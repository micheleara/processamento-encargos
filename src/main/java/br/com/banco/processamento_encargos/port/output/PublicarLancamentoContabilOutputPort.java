package br.com.banco.processamento_encargos.port.output;

import br.com.banco.processamento_encargos.core.domain.model.ResultadoProcessamento;

public interface PublicarLancamentoContabilOutputPort {

    void publicar(ResultadoProcessamento resultado);
}