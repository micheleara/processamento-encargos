package br.com.banco.processamento_encargos.adapter.output.producer.dto;

public record ConsultaContaRequestEvent(
        String correlationId,
        String numeroConta
) {
}