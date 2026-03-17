package br.com.banco.processamento_encargos.adapter.output.producer;

import br.com.banco.processamento_encargos.adapter.output.repository.LancamentoContabilPendenteJpaRepository;
import br.com.banco.processamento_encargos.adapter.output.repository.entity.LancamentoContabilPendenteEntity;
import br.com.banco.processamento_encargos.core.domain.model.Lancamento;
import br.com.banco.processamento_encargos.core.domain.model.ResultadoProcessamento;
import br.com.banco.processamento_encargos.core.domain.model.TipoLancamento;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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
class LancamentoContabilProducerTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private LancamentoContabilPendenteJpaRepository pendenteRepository;

    private ObjectMapper objectMapper;
    private LancamentoContabilProducer adapter;

    @BeforeEach
    void setUp() throws Exception {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        adapter = new LancamentoContabilProducer(kafkaTemplate, objectMapper, pendenteRepository, new SimpleMeterRegistry());
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
        verify(kafkaTemplate).send(topicoCaptor.capture(), chaveCaptor.capture(), anyString());

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
    @DisplayName("Não deve propagar exceção quando serialização falha — apenas loga e retorna")
    void naoDevePropagarExcecaoQuandoSerializacaoFalha() throws Exception {
        ObjectMapper objectMapperMock = mock(ObjectMapper.class);
        when(objectMapperMock.writeValueAsString(any())).thenThrow(new RuntimeException("Erro de serialização"));

        LancamentoContabilProducer adapterComErro = new LancamentoContabilProducer(
                kafkaTemplate, objectMapperMock, pendenteRepository, new SimpleMeterRegistry());
        setField(adapterComErro, "topicoLancamentoContabil", "encargos.lancamento-contabil");

        assertDoesNotThrow(() -> adapterComErro.publicar(criarResultadoProcessado()));
        verifyNoInteractions(kafkaTemplate);
        verifyNoInteractions(pendenteRepository);
    }

    @Test
    @DisplayName("Deve persistir evento no outbox quando Kafka falha")
    void devePersistirNoOutboxQuandoKafkaFalha() {
        doThrow(new RuntimeException("Kafka indisponível"))
                .when(kafkaTemplate).send(anyString(), anyString(), anyString());
        when(pendenteRepository.existsByIdLancamento("TXN-001")).thenReturn(false);

        adapter.publicar(criarResultadoProcessado());

        ArgumentCaptor<LancamentoContabilPendenteEntity> captor =
                ArgumentCaptor.forClass(LancamentoContabilPendenteEntity.class);
        verify(pendenteRepository).save(captor.capture());

        LancamentoContabilPendenteEntity entity = captor.getValue();
        assertEquals("TXN-001", entity.getIdLancamento());
        assertEquals(1, entity.getTentativas());
        assertNotNull(entity.getPayload());
        assertNotNull(entity.getCriadoEm());
    }

    @Test
    @DisplayName("Deve publicar uma vez por chamada")
    void devePublicarUmaVezPorChamada() {
        adapter.publicar(criarResultadoProcessado());

        verify(kafkaTemplate, times(1)).send(anyString(), anyString(), anyString());
    }
}