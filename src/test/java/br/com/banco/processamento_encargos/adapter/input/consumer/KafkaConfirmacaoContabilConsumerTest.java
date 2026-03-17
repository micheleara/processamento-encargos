package br.com.banco.processamento_encargos.adapter.input.consumer;

import br.com.banco.processamento_encargos.adapter.output.producer.dto.ConfirmacaoContabilEvent;
import br.com.banco.processamento_encargos.port.output.PublicarAtualizacaoSaldoOutputPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KafkaConfirmacaoContabilConsumerTest {

    @Mock
    private PublicarAtualizacaoSaldoOutputPort publicarAtualizacaoSaldoPort;

    @Mock
    private Acknowledgment ack;

    private ObjectMapper objectMapper;
    private KafkaConfirmacaoContabilConsumer consumer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        consumer = new KafkaConfirmacaoContabilConsumer(publicarAtualizacaoSaldoPort, objectMapper);
    }

    @Test
    @DisplayName("Deve chamar publicarSaldo quando status é PROCESSADO")
    void deveChamarPublicarSaldoQuandoStatusProcessado() throws Exception {
        ConfirmacaoContabilEvent evento = new ConfirmacaoContabilEvent(
                "EVT-001", "LC-1710000000000-a1b2c3d4", "PROCESSADO", LocalDateTime.now());
        String payload = objectMapper.writeValueAsString(evento);

        consumer.consumirConfirmacao(payload, "encargos.contabil-confirmacao", ack);

        verify(publicarAtualizacaoSaldoPort).publicarSaldo("EVT-001");
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("Não deve chamar publicarSaldo quando status é REJEITADO")
    void naoDeveChamarPublicarSaldoQuandoStatusRejeitado() throws Exception {
        ConfirmacaoContabilEvent evento = new ConfirmacaoContabilEvent(
                "EVT-002", "LC-1710000000000-b2c3d4e5", "REJEITADO", LocalDateTime.now());
        String payload = objectMapper.writeValueAsString(evento);

        consumer.consumirConfirmacao(payload, "encargos.contabil-confirmacao", ack);

        verify(publicarAtualizacaoSaldoPort, never()).publicarSaldo(any());
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("Deve lançar exceção com JSON inválido — DefaultErrorHandler encaminha para DLT")
    void deveLancarExcecaoComJsonInvalido() {
        assertThrows(Exception.class, () ->
                consumer.consumirConfirmacao("{ json invalido }", "encargos.contabil-confirmacao", ack));

        verify(publicarAtualizacaoSaldoPort, never()).publicarSaldo(any());
        verify(ack, never()).acknowledge();
    }

    @Test
    @DisplayName("Deve lançar exceção quando publicarSaldo falha — DefaultErrorHandler gerencia retry e DLT")
    void deveLancarExcecaoQuandoPublicarSaldoFalha() throws Exception {
        ConfirmacaoContabilEvent evento = new ConfirmacaoContabilEvent(
                "EVT-ERR", "LC-1710000000000-c3d4e5f6", "PROCESSADO", LocalDateTime.now());
        String payload = objectMapper.writeValueAsString(evento);
        doThrow(new RuntimeException("Erro ao publicar saldo"))
                .when(publicarAtualizacaoSaldoPort).publicarSaldo("EVT-ERR");

        assertThrows(RuntimeException.class, () ->
                consumer.consumirConfirmacao(payload, "encargos.contabil-confirmacao", ack));

        verify(ack, never()).acknowledge();
    }
}