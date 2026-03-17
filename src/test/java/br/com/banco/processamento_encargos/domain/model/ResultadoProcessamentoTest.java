package br.com.banco.processamento_encargos.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class ResultadoProcessamentoTest {

    private Lancamento criarLancamento() {
        return new Lancamento(
                "abc-123", "001234567-8", TipoLancamento.DEBITO,
                new BigDecimal("250.00"), LocalDate.of(2026, 3, 10), "Taxa mensal", "Debitar");
    }

    @Test
    @DisplayName("Factory processado deve criar resultado com status PROCESSADO e sem motivo de rejeição")
    void factoryProcessadoDeveCriarResultadoCorreto() {
        Lancamento lancamento = criarLancamento();

        ResultadoProcessamento resultado = ResultadoProcessamento.processado(
                lancamento, new BigDecimal("5000.00"), new BigDecimal("4750.00"));

        assertEquals(StatusProcessamento.PROCESSADO, resultado.status());
        assertNull(resultado.motivoRejeicao());
        assertEquals("abc-123", resultado.idLancamento());
        assertEquals("001234567-8", resultado.numeroConta());
        assertEquals(TipoLancamento.DEBITO, resultado.tipoLancamento());
        assertEquals(new BigDecimal("250.00"), resultado.valor());
        assertEquals(LocalDate.of(2026, 3, 10), resultado.dataLancamento());
        assertEquals("Taxa mensal", resultado.descricao());
        assertEquals(new BigDecimal("5000.00"), resultado.saldoAnterior());
        assertEquals(new BigDecimal("4750.00"), resultado.saldoPosterior());
        assertNotNull(resultado.dataProcessamento());
    }

    @Test
    @DisplayName("Factory rejeitado deve criar resultado com status REJEITADO e motivo informado")
    void factoryRejeitadoDeveCriarResultadoCorreto() {
        Lancamento lancamento = criarLancamento();

        ResultadoProcessamento resultado = ResultadoProcessamento.rejeitado(lancamento, "CONTA_CANCELADA");

        assertEquals(StatusProcessamento.REJEITADO, resultado.status());
        assertEquals("CONTA_CANCELADA", resultado.motivoRejeicao());
        assertEquals("abc-123", resultado.idLancamento());
        assertEquals("001234567-8", resultado.numeroConta());
        assertNotNull(resultado.dataProcessamento());
    }

    @Test
    @DisplayName("Factory rejeitado deve aceitar motivo nulo (sem persistência)")
    void factoryRejeitadoDeveAceitarMotivoNulo() {
        Lancamento lancamento = criarLancamento();

        ResultadoProcessamento resultado = ResultadoProcessamento.rejeitado(lancamento, null);

        assertEquals(StatusProcessamento.REJEITADO, resultado.status());
        assertNull(resultado.motivoRejeicao());
    }
}

