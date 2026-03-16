package br.com.banco.processamento_encargos.adapter.out.stub;

import br.com.banco.processamento_encargos.domain.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class StubAdaptersTest {

    @Test
    @DisplayName("StubConsultarClienteContaAdapter deve retornar conta ATIVA com saldo")
    void stubConsultarClienteDeveRetornarContaAtiva() {
        StubConsultarClienteContaAdapter stub = new StubConsultarClienteContaAdapter();

        ContaInfo conta = stub.consultarCliente("001234567-8", "corr-123");

        assertEquals("001234567-8", conta.numeroConta());
        assertEquals("Cliente Stub", conta.nomeCliente());
        assertEquals(StatusConta.ATIVA, conta.status());
        assertEquals(new BigDecimal("10000.00"), conta.saldo());
    }

    @Test

    @DisplayName("StubAtualizarSaldoContaAdapter deve executar sem exceção")
    void stubAtualizarSaldoDeveExecutarSemExcecao() {
        StubAtualizarSaldoContaAdapter stub = new StubAtualizarSaldoContaAdapter();

        assertDoesNotThrow(() ->
                stub.publicarAtualizacaoSaldo("id-001", "001234567-8", TipoLancamento.DEBITO, new BigDecimal("100.00")));
    }
}