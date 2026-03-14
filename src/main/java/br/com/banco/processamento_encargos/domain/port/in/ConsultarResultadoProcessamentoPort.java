package br.com.banco.processamento_encargos.domain.port.in;

import br.com.banco.processamento_encargos.domain.model.ResultadoProcessamento;
import br.com.banco.processamento_encargos.domain.model.StatusProcessamento;

import java.util.List;
import java.util.Optional;

public interface ConsultarResultadoProcessamentoPort {

    Optional<ResultadoProcessamento> consultarPorIdLancamento(String idLancamento);

    List<ResultadoProcessamento> consultarPorNumeroConta(String numeroConta);

    List<ResultadoProcessamento> consultarPorStatus(StatusProcessamento status);
}

