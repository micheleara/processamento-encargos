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
    @DisplayName("Factory processado deve criar resultado com status PROCESSADO, saldos e sem motivo de recusa")
    void factoryProcessadoDeveCriarResultadoCorreto() {
        Lancamento lancamento = criarLancamento();
        BigDecimal saldoAnterior = new BigDecimal("5000.00");
        BigDecimal saldoPosterior = new BigDecimal("4750.00");

        ResultadoProcessamento resultado = ResultadoProcessamento.processado(lancamento, saldoAnterior, saldoPosterior);

        assertEquals(StatusProcessamento.PROCESSADO, resultado.status());
        assertNull(resultado.motivoRecusa());
        assertEquals(saldoAnterior, resultado.saldoAnterior());
        assertEquals(saldoPosterior, resultado.saldoPosterior());
        assertEquals("abc-123", resultado.idLancamento());
        assertEquals("001234567-8", resultado.numeroConta());
        assertEquals(TipoLancamento.DEBITO, resultado.tipoLancamento());
        assertEquals(new BigDecimal("250.00"), resultado.valor());
        assertEquals(LocalDate.of(2026, 3, 10), resultado.dataLancamento());
        assertEquals("Taxa mensal", resultado.descricao());
        assertNotNull(resultado.dataProcessamento());
    }

    @Test
    @DisplayName("Factory recusado deve criar resultado com status RECUSADO, saldos nulos e motivo informado")
    void factoryRecusadoDeveCriarResultadoCorreto() {
        Lancamento lancamento = criarLancamento();

        ResultadoProcessamento resultado = ResultadoProcessamento.recusado(lancamento, "CONTA_CANCELADA");

        assertEquals(StatusProcessamento.RECUSADO, resultado.status());
        assertEquals("CONTA_CANCELADA", resultado.motivoRecusa());
        assertNull(resultado.saldoAnterior());
        assertNull(resultado.saldoPosterior());
        assertEquals("abc-123", resultado.idLancamento());
        assertEquals("001234567-8", resultado.numeroConta());
        assertNotNull(resultado.dataProcessamento());
    }

    @Test
    @DisplayName("Factory recusado deve aceitar motivo nulo")
    void factoryRecusadoDeveAceitarMotivoNulo() {
        Lancamento lancamento = criarLancamento();

        ResultadoProcessamento resultado = ResultadoProcessamento.recusado(lancamento, null);

        assertEquals(StatusProcessamento.RECUSADO, resultado.status());
        assertNull(resultado.motivoRecusa());
    }
}