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
                new BigDecimal("150.75"), LocalDate.of(2026, 3, 15), "Encargo mensal", "DEBITAR");
    }

    @Test
    @DisplayName("Deve criar Lancamento com todos os campos corretamente")
    void deveCriarComTodosOsCampos() {
        Lancamento lancamento = criarLancamento();

        assertEquals("TXN-001",             lancamento.idLancamento());
        assertEquals("001234567-8",          lancamento.numeroConta());
        assertEquals(TipoLancamento.DEBITO,  lancamento.tipoLancamento());
        assertEquals(new BigDecimal("150.75"), lancamento.valor());
        assertEquals(LocalDate.of(2026, 3, 15), lancamento.dataLancamento());
        assertEquals("Encargo mensal",       lancamento.descricao());
        assertEquals("DEBITAR",              lancamento.evento());
    }

    @Test
    @DisplayName("Dois lancamentos com mesmos valores devem ser iguais")
    void doisLancamentosIguaisDevemSerIguais() {
        Lancamento l1 = criarLancamento();
        Lancamento l2 = criarLancamento();

        assertEquals(l1, l2);
        assertEquals(l1.hashCode(), l2.hashCode());
    }

    @Test
    @DisplayName("Lancamentos com ids diferentes devem ser diferentes")
    void lancamentosComIdsDiferentesDevemSerDiferentes() {
        Lancamento l1 = new Lancamento("TXN-001", "001", TipoLancamento.DEBITO,
                BigDecimal.ONE, LocalDate.now(), "desc", "DEBITAR");
        Lancamento l2 = new Lancamento("TXN-002", "001", TipoLancamento.DEBITO,
                BigDecimal.ONE, LocalDate.now(), "desc", "DEBITAR");

        assertNotEquals(l1, l2);
    }

    @Test
    @DisplayName("Deve criar Lancamento de tipo CREDITO")
    void deveCriarLancamentoCredito() {
        Lancamento lancamento = new Lancamento(
                "TXN-002", "001234567-8", TipoLancamento.CREDITO,
                new BigDecimal("300.00"), LocalDate.of(2026, 3, 15), "Crédito", "CREDITAR");

        assertEquals(TipoLancamento.CREDITO, lancamento.tipoLancamento());
        assertEquals("CREDITAR", lancamento.evento());
    }

    @Test
    @DisplayName("toString deve conter os dados do lancamento")
    void toStringDeveConterDados() {
        Lancamento lancamento = criarLancamento();

        String str = lancamento.toString();

        assertTrue(str.contains("TXN-001"));
        assertTrue(str.contains("001234567-8"));
    }
}