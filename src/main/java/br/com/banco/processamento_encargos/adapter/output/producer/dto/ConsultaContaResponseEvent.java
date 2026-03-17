package br.com.banco.processamento_encargos.adapter.output.producer.dto;

import java.math.BigDecimal;

public record ConsultaContaResponseEvent(
        String correlationId,
        String numeroConta,
        String nomeCliente,
        String status,
        BigDecimal saldo
) {
}