package br.com.banco.processamento_encargos.adapter.output.producer.dto;

import java.math.BigDecimal;

public record LancamentoContabilEvent(
        String idLancamento,
        String numConta,
        BigDecimal valor,
        String descricao,
        BigDecimal saldoAnterior,
        BigDecimal saldoPosterior
) {}