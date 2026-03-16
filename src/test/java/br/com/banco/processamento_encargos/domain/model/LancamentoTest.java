package br.com.banco.processamento_encargos.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class LancamentoTest {

    private Lancamento criarLancamento() {
        return new Lancamento(
                "TXN-001", "001234567-8", TipoLancamento.DEBITO,
                new BigDecimal("250.00"), LocalDate.of(2026, 3, 15), "Taxa mensal", "DEBITAR");
    }

    @Test
    @DisplayName("Deve criar lançamento com todos os campos corretos")
    void deveCriarLancamentoComCamposCorretos() {
        Lancamento lancamento = criarLancamento();

        assertEquals("TXN-001", lancamento.idLancamento());
        assertEquals("001234567-8", lancamento.numeroConta());
        assertEquals(TipoLancamento.DEBITO, lancamento.tipoLancamento());
        assertEquals(new BigDecimal("250.00"), lancamento.valor());
        assertEquals(LocalDate.of(2026, 3, 15), lancamento.dataLancamento());
        assertEquals("Taxa mensal", lancamento.descricao());
        assertEquals("DEBITAR", lancamento.evento());
    }

    @Test
    @DisplayName("Dois lançamentos com mesmos campos devem ser iguais")
    void doisLancamentosComMesmosCamposDevemSerIguais() {
        Lancamento l1 = criarLancamento();
        Lancamento l2 = criarLancamento();

        assertEquals(l1, l2);
        assertEquals(l1.hashCode(), l2.hashCode());
    }

    @Test
    @DisplayName("Lançamentos com campos diferentes não devem ser iguais")
    void lancamentosComCamposDiferentesNaoDevemSerIguais() {
        Lancamento l1 = criarLancamento();
        Lancamento l2 = new Lancamento(
                "TXN-002", "001234567-8", TipoLancamento.CREDITO,
                new BigDecimal("100.00"), LocalDate.of(2026, 3, 15), "Taxa mensal", "CREDITAR");

        assertNotEquals(l1, l2);
    }

    @Test
    @DisplayName("Deve suportar tipo CREDITO")
    void deveSuportarTipoCredito() {
        Lancamento lancamento = new Lancamento(
                "TXN-002", "009876543-1", TipoLancamento.CREDITO,
                new BigDecimal("500.00"), LocalDate.of(2026, 3, 15), "Estorno", "CREDITAR");

        assertEquals(TipoLancamento.CREDITO, lancamento.tipoLancamento());
    }

    @Test
    @DisplayName("toString deve conter os campos principais")
    void toStringDeveConterCamposPrincipais() {
        Lancamento lancamento = criarLancamento();
        String str = lancamento.toString();

        assertTrue(str.contains("TXN-001"));
        assertTrue(str.contains("001234567-8"));
    }
}