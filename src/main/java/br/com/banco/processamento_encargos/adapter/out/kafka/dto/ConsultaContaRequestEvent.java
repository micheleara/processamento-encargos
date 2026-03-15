package br.com.banco.processamento_encargos.adapter.out.kafka.dto;

public record ConsultaContaRequestEvent(
        String correlationId,
        String numeroConta
) {
}