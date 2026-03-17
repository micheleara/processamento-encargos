package br.com.banco.processamento_encargos.port.input;

import br.com.banco.processamento_encargos.core.domain.model.ResultadoProcessamento;
import br.com.banco.processamento_encargos.core.domain.model.StatusProcessamento;

import java.util.List;
import java.util.Optional;

public interface ConsultarResultadoProcessamentoInputPort {

    Optional<ResultadoProcessamento> consultarPorIdLancamento(String idLancamento);

    List<ResultadoProcessamento> consultarPorNumeroConta(String numeroConta);

    List<ResultadoProcessamento> consultarPorStatus(StatusProcessamento status);
}
