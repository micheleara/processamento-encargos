package br.com.banco.processamento_encargos.core.domain.model;

import java.math.BigDecimal;

public record ContaInfo(
        String numeroConta,
        String nomeCliente,
        StatusConta status,
        BigDecimal saldo
) {

    public static ContaInfo indisponivel(String numeroConta) {
        return new ContaInfo(numeroConta, null, StatusConta.INDISPONIVEL, BigDecimal.ZERO);
    }
}
