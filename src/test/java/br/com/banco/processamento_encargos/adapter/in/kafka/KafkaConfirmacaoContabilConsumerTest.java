package br.com.banco.processamento_encargos.adapter.in.kafka;

import br.com.banco.processamento_encargos.adapter.out.kafka.KafkaAtualizarSaldoContaAdapter;
import br.com.banco.processamento_encargos.adapter.out.kafka.dto.ConfirmacaoContabilEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.time.LocalDateTime;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KafkaConfirmacaoContabilConsumerTest {

    @Mock
    private KafkaAtualizarSaldoContaAdapter kafkaAtualizarSaldoContaAdapter;

    @Mock
    private Acknowledgment ack;

    private ObjectMapper objectMapper;
    private KafkaConfirmacaoContabilConsumer consumer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        consumer = new KafkaConfirmacaoContabilConsumer(kafkaAtualizarSaldoContaAdapter, objectMapper);
    }

    @Test
    @DisplayName("Deve chamar publicarSaldo quando status é PROCESSADO")
    void deveChamarPublicarSaldoQuandoStatusProcessado() throws Exception {
        ConfirmacaoContabilEvent evento = new ConfirmacaoContabilEvent(
                "EVT-001", "LC-1710000000000-a1b2c3d4", "PROCESSADO", LocalDateTime.now());
        String payload = objectMapper.writeValueAsString(evento);

        consumer.consumirConfirmacao(payload, "encargos.contabil-confirmacao", ack);

        verify(kafkaAtualizarSaldoContaAdapter).publicarSaldo("EVT-001");
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("Não deve chamar publicarSaldo quando status é RECUSADO")
    void naoDeveChamarPublicarSaldoQuandoStatusRecusado() throws Exception {
        ConfirmacaoContabilEvent evento = new ConfirmacaoContabilEvent(
                "EVT-002", "LC-1710000000000-b2c3d4e5", "RECUSADO", LocalDateTime.now());
        String payload = objectMapper.writeValueAsString(evento);

        consumer.consumirConfirmacao(payload, "encargos.contabil-confirmacao", ack);

        verify(kafkaAtualizarSaldoContaAdapter, never()).publicarSaldo(any());
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("Não deve lançar exceção com JSON inválido")
    void naoDeveLancarExcecaoComJsonInvalido() {
        String payloadInvalido = "{ json invalido }";

        consumer.consumirConfirmacao(payloadInvalido, "encargos.contabil-confirmacao", ack);

        verify(kafkaAtualizarSaldoContaAdapter, never()).publicarSaldo(any());
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("Deve sempre chamar ack mesmo em caso de erro")
    void deveSempreChamarAck() {
        String payloadInvalido = "INVALID_JSON";

        consumer.consumirConfirmacao(payloadInvalido, "encargos.contabil-confirmacao", ack);

        verify(ack).acknowledge();
    }
}