package br.com.banco.processamento_encargos.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ResultadoProcessamento(
        String idLancamento,
        String numeroConta,
        TipoLancamento tipoLancamento,
        BigDecimal valor,
        LocalDate dataLancamento,
        String descricao,
        String evento,
        StatusProcessamento status,
        String motivoRejeicao,
        BigDecimal saldoAnterior,
        BigDecimal saldoPosterior,
        LocalDateTime dataProcessamento
) {

    public static ResultadoProcessamento processado(Lancamento lancamento, BigDecimal saldoAnterior, BigDecimal saldoPosterior) {
        return new ResultadoProcessamento(
                lancamento.idLancamento(),
                lancamento.numeroConta(),
                lancamento.tipoLancamento(),
                lancamento.valor(),
                lancamento.dataLancamento(),
                lancamento.descricao(),
                lancamento.evento().toUpperCase(),
                StatusProcessamento.PROCESSADO,
                null,
                saldoAnterior,
                saldoPosterior,
                LocalDateTime.now()
        );
    }

    public static ResultadoProcessamento rejeitado(Lancamento lancamento, String motivoRejeicao) {
        return new ResultadoProcessamento(
                lancamento.idLancamento(),
                lancamento.numeroConta(),
                lancamento.tipoLancamento(),
                lancamento.valor(),
                lancamento.dataLancamento(),
                lancamento.descricao(),
                lancamento.evento().toUpperCase(),
                StatusProcessamento.RECUSADO,
                motivoRejeicao,
                null,
                null,
                LocalDateTime.now()
        );
    }
}