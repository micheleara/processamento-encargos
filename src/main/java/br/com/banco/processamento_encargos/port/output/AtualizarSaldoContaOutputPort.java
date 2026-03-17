package br.com.banco.processamento_encargos.port.output;

import br.com.banco.processamento_encargos.core.domain.model.TipoLancamento;

import java.math.BigDecimal;

public interface AtualizarSaldoContaOutputPort {

    void publicarAtualizacaoSaldo(String idLancamento, String numeroConta, TipoLancamento tipo, BigDecimal valor);
}
