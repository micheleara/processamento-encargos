package br.com.banco.processamento_encargos.core.usecase;

import br.com.banco.processamento_encargos.core.domain.model.ContaInfo;
import br.com.banco.processamento_encargos.core.domain.model.Lancamento;
import br.com.banco.processamento_encargos.core.domain.model.StatusConta;
import br.com.banco.processamento_encargos.core.domain.model.TipoLancamento;

import java.util.Optional;

public class ValidacaoLancamentoUseCase {

    public Optional<String> validar(Lancamento lancamento, ContaInfo conta) {
        if (conta.status() == StatusConta.INDISPONIVEL) {
            return Optional.of("SISTEMA_CONTAS_INDISPONIVEL");
        }

        if (conta.status() == StatusConta.CANCELADA) {
            return Optional.of("CONTA_CANCELADA");
        }

        if (lancamento.tipoLancamento() == TipoLancamento.DEBITO
                && conta.status() == StatusConta.BLOQUEIO_JUDICIAL) {
            return Optional.of("CONTA_COM_BLOQUEIO_JUDICIAL");
        }

        return Optional.empty();
    }
}