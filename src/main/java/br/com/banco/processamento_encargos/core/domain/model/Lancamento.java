package br.com.banco.processamento_encargos.core.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record Lancamento(
        String idLancamento,
        String numeroConta,
        TipoLancamento tipoLancamento,
        BigDecimal valor,
        LocalDate dataLancamento,
        String descricao,
        String evento
) {
}
