package br.com.banco.processamento_encargos.adapter.out.kafka.dto;

import java.math.BigDecimal;

public record AtualizarSaldoEvent(
        String idLancamento,
        String numeroConta,
        String tipoLancamento,
        BigDecimal valor
) {}