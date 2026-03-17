package br.com.banco.processamento_encargos.adapter.output.producer;

import br.com.banco.processamento_encargos.adapter.output.repository.SaldoPendenteJpaRepository;
import br.com.banco.processamento_encargos.adapter.output.repository.entity.SaldoPendenteEntity;
import br.com.banco.processamento_encargos.core.domain.model.TipoLancamento;
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
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AtualizarSaldoContaProducerTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private SaldoPendenteJpaRepository saldoPendenteRepository;

    private AtualizarSaldoContaProducer adapter;

    @BeforeEach
    void setUp() throws Exception {
        adapter = new AtualizarSaldoContaProducer(kafkaTemplate, objectMapper, saldoPendenteRepository);
        setField(adapter, "topicoAtualizarSaldo", "encargos.conta.atualizar-saldo");
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    @DisplayName("publicarAtualizacaoSaldo deve persistir evento no banco")
    void devePersistirEventoNoBanco() {
        when(saldoPendenteRepository.existsByIdLancamento("EVT-001")).thenReturn(false);

        adapter.publicarAtualizacaoSaldo("EVT-001", "001234567-8", TipoLancamento.DEBITO, new BigDecimal("150.75"));

        ArgumentCaptor<SaldoPendenteEntity> captor = ArgumentCaptor.forClass(SaldoPendenteEntity.class);
        verify(saldoPendenteRepository).save(captor.capture());

        SaldoPendenteEntity entity = captor.getValue();
        assertEquals("EVT-001", entity.getIdLancamento());
        assertEquals("001234567-8", entity.getNumConta());
        assertEquals("DEBITO", entity.getTipoLancamento());
        assertEquals(new BigDecimal("150.75"), entity.getValor());
        assertNotNull(entity.getCriadoEm());
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    @DisplayName("publicarAtualizacaoSaldo deve ignorar duplicata já registrada")
    void deveIgnorarDuplicata() {
        when(saldoPendenteRepository.existsByIdLancamento("EVT-DUP")).thenReturn(true);

        adapter.publicarAtualizacaoSaldo("EVT-DUP", "001234567-8", TipoLancamento.DEBITO, new BigDecimal("50.00"));

        verify(saldoPendenteRepository, never()).save(any());
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    @DisplayName("publicarSaldo deve publicar no Kafka e deletar do banco")
    void devePublicarSaldoEDeletarDoBanco() throws Exception {
        SaldoPendenteEntity entity = SaldoPendenteEntity.builder()
                .idLancamento("EVT-002")
                .numConta("009876543-1")
                .tipoLancamento("CREDITO")
                .valor(new BigDecimal("200.00"))
                .criadoEm(LocalDateTime.now())
                .build();
        when(saldoPendenteRepository.findByIdLancamento("EVT-002")).thenReturn(Optional.of(entity));
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"payload\":\"ok\"}");

        adapter.publicarSaldo("EVT-002");

        verify(kafkaTemplate).send(eq("encargos.conta.atualizar-saldo"), eq("009876543-1"), anyString());
        verify(saldoPendenteRepository).delete(entity);
    }

    @Test
    @DisplayName("publicarSaldo com idLancamento desconhecido não deve enviar Kafka")
    void deveIgnorarIdDesconhecido() {
        when(saldoPendenteRepository.findByIdLancamento("ID-INEXISTENTE")).thenReturn(Optional.empty());

        adapter.publicarSaldo("ID-INEXISTENTE");

        verifyNoInteractions(kafkaTemplate);
    }
}