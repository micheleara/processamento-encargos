package br.com.banco.processamento_encargos.adapter.out.kafka.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class ConsultaContaEventosTest {

    @Test
    @DisplayName("ConsultaContaRequestEvent deve armazenar correlationId e numeroConta")
    void requestEventDeveArmazenarCampos() {
        ConsultaContaRequestEvent event = new ConsultaContaRequestEvent("corr-001", "001234567-8");

        assertEquals("corr-001",    event.correlationId());
        assertEquals("001234567-8", event.numeroConta());
    }

    @Test
    @DisplayName("ConsultaContaRequestEvent deve implementar equals/hashCode por valor")
    void requestEventEquality() {
        ConsultaContaRequestEvent e1 = new ConsultaContaRequestEvent("corr-001", "001");
        ConsultaContaRequestEvent e2 = new ConsultaContaRequestEvent("corr-001", "001");

        assertEquals(e1, e2);
        assertEquals(e1.hashCode(), e2.hashCode());
    }

    @Test
    @DisplayName("ConsultaContaResponseEvent deve armazenar todos os campos")
    void responseEventDeveArmazenarCampos() {
        ConsultaContaResponseEvent event = new ConsultaContaResponseEvent(
                "corr-002", "001234567-8", "João Silva", "ATIVA", new BigDecimal("5000.00"));

        assertEquals("corr-002",              event.correlationId());
        assertEquals("001234567-8",            event.numeroConta());
        assertEquals("João Silva",             event.nomeCliente());
        assertEquals("ATIVA",                  event.status());
        assertEquals(new BigDecimal("5000.00"), event.saldo());
    }

    @Test
    @DisplayName("ConsultaContaResponseEvent deve aceitar status INDISPONIVEL e saldo zero")
    void responseEventDevAceitarIndisponivel() {
        ConsultaContaResponseEvent event = new ConsultaContaResponseEvent(
                "corr-003", "001", null, "INDISPONIVEL", BigDecimal.ZERO);

        assertEquals("INDISPONIVEL", event.status());
        assertNull(event.nomeCliente());
        assertEquals(BigDecimal.ZERO, event.saldo());
    }

    @Test
    @DisplayName("ConsultaContaResponseEvent deve implementar equals/hashCode por valor")
    void responseEventEquality() {
        ConsultaContaResponseEvent e1 = new ConsultaContaResponseEvent("c", "n", "nome", "ATIVA", BigDecimal.ONE);
        ConsultaContaResponseEvent e2 = new ConsultaContaResponseEvent("c", "n", "nome", "ATIVA", BigDecimal.ONE);

        assertEquals(e1, e2);
        assertEquals(e1.hashCode(), e2.hashCode());
    }
}