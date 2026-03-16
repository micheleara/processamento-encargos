package br.com.banco.processamento_encargos.adapter.out.kafka;

import br.com.banco.processamento_encargos.domain.model.Lancamento;
import br.com.banco.processamento_encargos.domain.model.ResultadoProcessamento;
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
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KafkaLancamentoContabilAdapterTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private ObjectMapper objectMapper;
    private KafkaLancamentoContabilAdapter adapter;

    @BeforeEach
    void setUp() throws Exception {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        adapter = new KafkaLancamentoContabilAdapter(kafkaTemplate, objectMapper);
        setField(adapter, "topicoLancamentoContabil", "encargos.lancamento-contabil");
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private ResultadoProcessamento criarResultadoProcessado() {
        Lancamento lancamento = new Lancamento(
                "TXN-001", "001234567-8", TipoLancamento.DEBITO,
                new BigDecimal("150.75"), LocalDate.of(2026, 3, 15), "Encargos mensais", "DEBITAR");
        return ResultadoProcessamento.processado(lancamento, new BigDecimal("1000.00"), new BigDecimal("849.25"));
    }

    @Test
    @DisplayName("Deve publicar evento no tópico correto com idLancamento como chave")
    void devePublicarNoTopicoCorretoComChaveCorreta() {
        ResultadoProcessamento resultado = criarResultadoProcessado();

        adapter.publicar(resultado);

        ArgumentCaptor<String> topicoCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> chaveCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(topicoCaptor.capture(), chaveCaptor.capture(), payloadCaptor.capture());

        assertEquals("encargos.lancamento-contabil", topicoCaptor.getValue());
        assertEquals("TXN-001", chaveCaptor.getValue());
    }

    @Test
    @DisplayName("Deve serializar os campos corretos no payload")
    void deveSerializarCamposCorretosNoPayload() throws Exception {
        ResultadoProcessamento resultado = criarResultadoProcessado();

        adapter.publicar(resultado);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(anyString(), anyString(), payloadCaptor.capture());

        String payload = payloadCaptor.getValue();
        assertTrue(payload.contains("TXN-001"));
        assertTrue(payload.contains("001234567-8"));
        assertTrue(payload.contains("150.75"));
        assertTrue(payload.contains("Encargos mensais"));
        assertTrue(payload.contains("1000.00"));
        assertTrue(payload.contains("849.25"));
    }

    @Test
    @DisplayName("Deve lançar RuntimeException quando serialização falha")
    void deveLancarRuntimeExceptionQuandoSerializacaoFalha() throws Exception {
        ObjectMapper objectMapperMock = mock(ObjectMapper.class);
        when(objectMapperMock.writeValueAsString(any())).thenThrow(new RuntimeException("Erro de serialização"));

        KafkaLancamentoContabilAdapter adapterComErro = new KafkaLancamentoContabilAdapter(kafkaTemplate, objectMapperMock);
        setField(adapterComErro, "topicoLancamentoContabil", "encargos.lancamento-contabil");

        ResultadoProcessamento resultado = criarResultadoProcessado();

        RuntimeException ex = assertThrows(RuntimeException.class, () -> adapterComErro.publicar(resultado));
        assertTrue(ex.getMessage().contains("Falha ao publicar lançamento contábil"));
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    @DisplayName("Deve publicar uma vez por chamada")
    void devePublicarUmaVezPorChamada() {
        ResultadoProcessamento resultado = criarResultadoProcessado();

        adapter.publicar(resultado);

        verify(kafkaTemplate, times(1)).send(anyString(), anyString(), anyString());
    }
}