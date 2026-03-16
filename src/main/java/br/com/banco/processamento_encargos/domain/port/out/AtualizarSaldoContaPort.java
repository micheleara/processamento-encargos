package br.com.banco.processamento_encargos.domain.port.out;

import br.com.banco.processamento_encargos.domain.model.TipoLancamento;

import java.math.BigDecimal;

public interface AtualizarSaldoContaPort {

    void publicarAtualizacaoSaldo(String idLancamento, String numeroConta, TipoLancamento tipo, BigDecimal valor);
}

