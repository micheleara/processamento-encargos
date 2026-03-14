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
        StatusProcessamento status,
        String motivoRejeicao,
        LocalDateTime dataProcessamento
) {

    public static ResultadoProcessamento processado(Lancamento lancamento) {
        return new ResultadoProcessamento(
                lancamento.idLancamento(),
                lancamento.numeroConta(),
                lancamento.tipoLancamento(),
                lancamento.valor(),
                lancamento.dataLancamento(),
                lancamento.descricao(),
                StatusProcessamento.PROCESSADO,
                null,
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
                StatusProcessamento.REJEITADO,
                motivoRejeicao,
                LocalDateTime.now()
        );
    }
}

