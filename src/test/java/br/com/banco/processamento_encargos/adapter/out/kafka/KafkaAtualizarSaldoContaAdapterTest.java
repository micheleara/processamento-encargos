package br.com.banco.processamento_encargos.adapter.out.kafka;

import br.com.banco.processamento_encargos.adapter.out.kafka.dto.AtualizarSaldoEvent;
import br.com.banco.processamento_encargos.domain.model.TipoLancamento;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KafkaAtualizarSaldoContaAdapterTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private ObjectMapper objectMapper;

    private KafkaAtualizarSaldoContaAdapter adapter;

    @BeforeEach
    void setUp() throws Exception {
        adapter = new KafkaAtualizarSaldoContaAdapter(kafkaTemplate, objectMapper);
        setField(adapter, "topicoAtualizarSaldo", "encargos.conta.atualizar-saldo");
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @SuppressWarnings("unchecked")
    private ConcurrentHashMap<String, AtualizarSaldoEvent> getPendentes() throws Exception {
        Field field = adapter.getClass().getDeclaredField("pendentes");
        field.setAccessible(true);
        return (ConcurrentHashMap<String, AtualizarSaldoEvent>) field.get(adapter);
    }

    @Test
    @DisplayName("publicarAtualizacaoSaldo deve armazenar evento no mapa pendentes")
    void deveArmazenarEventoNoPendentes() throws Exception {
        adapter.publicarAtualizacaoSaldo("EVT-001", "001234567-8", TipoLancamento.DEBITO, new BigDecimal("150.75"));

        ConcurrentHashMap<String, AtualizarSaldoEvent> pendentes = getPendentes();
        assertTrue(pendentes.containsKey("EVT-001"));
        AtualizarSaldoEvent evento = pendentes.get("EVT-001");
        assertEquals("EVT-001", evento.idLancamento());
        assertEquals("001234567-8", evento.numeroConta());
        assertEquals("DEBITO", evento.tipoLancamento());
        assertEquals(new BigDecimal("150.75"), evento.valor());
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    @DisplayName("publicarSaldo deve enviar mensagem para o topico correto e remover do mapa")
    void devePublicarSaldoNoTopicoCorreto() throws Exception {
        adapter.publicarAtualizacaoSaldo("EVT-002", "009876543-1", TipoLancamento.CREDITO, new BigDecimal("200.00"));

        String payloadEsperado = "{\"idLancamento\":\"EVT-002\",\"numeroConta\":\"009876543-1\",\"tipoLancamento\":\"CREDITO\",\"valor\":200.00}";
        when(objectMapper.writeValueAsString(any())).thenReturn(payloadEsperado);

        adapter.publicarSaldo("EVT-002");

        ArgumentCaptor<String> topicoCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> chaveCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(topicoCaptor.capture(), chaveCaptor.capture(), payloadCaptor.capture());

        assertEquals("encargos.conta.atualizar-saldo", topicoCaptor.getValue());
        assertEquals("009876543-1", chaveCaptor.getValue());
        assertEquals(payloadEsperado, payloadCaptor.getValue());

        ConcurrentHashMap<String, AtualizarSaldoEvent> pendentes = getPendentes();
        assertFalse(pendentes.containsKey("EVT-002"), "Evento deve ser removido do mapa após publicação");
    }

    @Test
    @DisplayName("publicarSaldo com idLancamento desconhecido deve logar aviso e não enviar mensagem")
    void deveIgnorarIdLancamentoDesconhecido() {
        adapter.publicarSaldo("ID-INEXISTENTE");

        verifyNoInteractions(kafkaTemplate);
    }
}