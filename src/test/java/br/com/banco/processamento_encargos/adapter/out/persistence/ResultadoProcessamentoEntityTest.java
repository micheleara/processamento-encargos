package br.com.banco.processamento_encargos.adapter.out.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class ResultadoProcessamentoEntityTest {

    @Test
    @DisplayName("Builder deve criar entidade com todos os campos preenchidos")
    void builderDeveCriarEntidadeCompleta() {
        LocalDateTime agora = LocalDateTime.now();

        ResultadoProcessamentoEntity entity = ResultadoProcessamentoEntity.builder()
                .idLancamento("TXN-001")
                .numConta("001234567-8")
                .tipoLancamento("DEBITO")
                .valor(new BigDecimal("200.00"))
                .dataLancamento(LocalDate.of(2026, 3, 15))
                .descricao("Encargo mensal")
                .evento("DEBITAR")
                .statusProc("PROCESSADO")
                .motivoRecusa(null)
                .saldoAnterior(new BigDecimal("5000.00"))
                .saldoPosterior(new BigDecimal("4800.00"))
                .dataProcessamento(agora)
                .build();

        assertEquals("TXN-001",          entity.getIdLancamento());
        assertEquals("001234567-8",       entity.getNumConta());
        assertEquals("DEBITO",            entity.getTipoLancamento());
        assertEquals(new BigDecimal("200.00"),  entity.getValor());
        assertEquals(LocalDate.of(2026, 3, 15), entity.getDataLancamento());
        assertEquals("Encargo mensal",    entity.getDescricao());
        assertEquals("DEBITAR",           entity.getEvento());
        assertEquals("PROCESSADO",        entity.getStatusProc());
        assertNull(entity.getMotivoRecusa());
        assertEquals(new BigDecimal("5000.00"), entity.getSaldoAnterior());
        assertEquals(new BigDecimal("4800.00"), entity.getSaldoPosterior());
        assertEquals(agora,               entity.getDataProcessamento());
    }

    @Test
    @DisplayName("Builder deve criar entidade RECUSADO com saldos nulos e motivo preenchido")
    void builderDeveCriarEntidadeRecusada() {
        ResultadoProcessamentoEntity entity = ResultadoProcessamentoEntity.builder()
                .idLancamento("TXN-002")
                .numConta("001234567-8")
                .tipoLancamento("DEBITO")
                .valor(new BigDecimal("100.00"))
                .dataLancamento(LocalDate.of(2026, 3, 15))
                .statusProc("RECUSADO")
                .motivoRecusa("CONTA_CANCELADA")
                .saldoAnterior(null)
                .saldoPosterior(null)
                .dataProcessamento(LocalDateTime.now())
                .build();

        assertEquals("RECUSADO",        entity.getStatusProc());
        assertEquals("CONTA_CANCELADA", entity.getMotivoRecusa());
        assertNull(entity.getSaldoAnterior());
        assertNull(entity.getSaldoPosterior());
    }

    @Test
    @DisplayName("Entidade criada sem id deve ter id nulo (gerado pelo banco)")
    void entidadeSemIdDeveTermIdNulo() {
        ResultadoProcessamentoEntity entity = ResultadoProcessamentoEntity.builder()
                .idLancamento("TXN-003")
                .numConta("001")
                .tipoLancamento("CREDITO")
                .valor(BigDecimal.ONE)
                .dataLancamento(LocalDate.now())
                .statusProc("PROCESSADO")
                .dataProcessamento(LocalDateTime.now())
                .build();

        assertNull(entity.getId());
    }
}