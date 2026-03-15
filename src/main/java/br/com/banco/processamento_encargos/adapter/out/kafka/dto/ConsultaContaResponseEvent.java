package br.com.banco.processamento_encargos.adapter.out.kafka.dto;

import java.math.BigDecimal;

public record ConsultaContaResponseEvent(
        String correlationId,
        String numeroConta,
        String nomeCliente,
        String status,
        BigDecimal saldo
) {
}