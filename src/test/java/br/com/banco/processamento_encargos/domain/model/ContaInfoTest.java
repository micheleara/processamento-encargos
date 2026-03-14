package br.com.banco.processamento_encargos.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class ContaInfoTest {

    @Test
    @DisplayName("Factory indisponivel deve criar ContaInfo com status INDISPONIVEL e saldo zero")
    void factoryIndisponivelDeveCriarContaComStatusCorreto() {
        ContaInfo conta = ContaInfo.indisponivel("001234567-8");

        assertEquals("001234567-8", conta.numeroConta());
        assertNull(conta.nomeCliente());
        assertEquals(StatusConta.INDISPONIVEL, conta.status());
        assertEquals(BigDecimal.ZERO, conta.saldo());
    }

    @Test
    @DisplayName("Deve criar ContaInfo com todos os campos preenchidos")
    void deveCriarContaInfoCompleta() {
        ContaInfo conta = new ContaInfo("009876543-2", "Maria Silva", StatusConta.ATIVA, new BigDecimal("5000.00"));

        assertEquals("009876543-2", conta.numeroConta());
        assertEquals("Maria Silva", conta.nomeCliente());
        assertEquals(StatusConta.ATIVA, conta.status());
        assertEquals(new BigDecimal("5000.00"), conta.saldo());
    }
}

