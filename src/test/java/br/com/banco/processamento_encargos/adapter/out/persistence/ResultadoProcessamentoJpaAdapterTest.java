package br.com.banco.processamento_encargos.adapter.out.persistence;

import br.com.banco.processamento_encargos.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ResultadoProcessamentoJpaAdapterTest {

    @Mock
    private ResultadoProcessamentoRepository repository;

    private ResultadoProcessamentoJpaAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new ResultadoProcessamentoJpaAdapter(repository);
    }

    private Lancamento criarLancamento() {
        return new Lancamento(
                "TXN-001", "001234567-8", TipoLancamento.DEBITO,
                new BigDecimal("200.00"), LocalDate.of(2026, 3, 15), "Encargo mensal", "DEBITAR");
    }

    @Test
    @DisplayName("Deve mapear todos os campos de resultado PROCESSADO para a entidade e salvar")
    void deveSalvarResultadoProcessado() {
        Lancamento lancamento = criarLancamento();
        ResultadoProcessamento resultado = ResultadoProcessamento.processado(
                lancamento, new BigDecimal("5000.00"), new BigDecimal("4800.00"));

        adapter.salvar(resultado);

        ArgumentCaptor<ResultadoProcessamentoEntity> captor =
                ArgumentCaptor.forClass(ResultadoProcessamentoEntity.class);
        verify(repository).save(captor.capture());

        ResultadoProcessamentoEntity entity = captor.getValue();
        assertEquals("TXN-001",           entity.getIdLancamento());
        assertEquals("001234567-8",        entity.getNumConta());
        assertEquals("DEBITO",             entity.getTipoLancamento());
        assertEquals(new BigDecimal("200.00"), entity.getValor());
        assertEquals(LocalDate.of(2026, 3, 15), entity.getDataLancamento());
        assertEquals("Encargo mensal",    entity.getDescricao());
        assertEquals("DEBITAR",           entity.getEvento());
        assertEquals("PROCESSADO",        entity.getStatusProc());
        assertNull(entity.getMotivoRecusa());
        assertEquals(new BigDecimal("5000.00"), entity.getSaldoAnterior());
        assertEquals(new BigDecimal("4800.00"), entity.getSaldoPosterior());
        assertNotNull(entity.getDataProcessamento());
    }

    @Test
    @DisplayName("Deve mapear todos os campos de resultado RECUSADO com saldos nulos")
    void deveSalvarResultadoRecusado() {
        Lancamento lancamento = criarLancamento();
        ResultadoProcessamento resultado = ResultadoProcessamento.recusado(lancamento, "CONTA_CANCELADA");

        adapter.salvar(resultado);

        ArgumentCaptor<ResultadoProcessamentoEntity> captor =
                ArgumentCaptor.forClass(ResultadoProcessamentoEntity.class);
        verify(repository).save(captor.capture());

        ResultadoProcessamentoEntity entity = captor.getValue();
        assertEquals("TXN-001",        entity.getIdLancamento());
        assertEquals("RECUSADO",       entity.getStatusProc());
        assertEquals("CONTA_CANCELADA", entity.getMotivoRecusa());
        assertNull(entity.getSaldoAnterior());
        assertNull(entity.getSaldoPosterior());
    }

    @Test
    @DisplayName("Deve salvar resultado RECUSADO sem motivo (motivo nulo)")
    void deveSalvarResultadoRecusadoSemMotivo() {
        Lancamento lancamento = criarLancamento();
        ResultadoProcessamento resultado = ResultadoProcessamento.recusado(lancamento, null);

        adapter.salvar(resultado);

        ArgumentCaptor<ResultadoProcessamentoEntity> captor =
                ArgumentCaptor.forClass(ResultadoProcessamentoEntity.class);
        verify(repository).save(captor.capture());

        assertNull(captor.getValue().getMotivoRecusa());
    }

    @Test
    @DisplayName("Deve salvar resultado com CREDITO mapeando tipo corretamente")
    void deveSalvarResultadoCredito() {
        Lancamento lancamento = new Lancamento(
                "TXN-002", "001234567-8", TipoLancamento.CREDITO,
                new BigDecimal("100.00"), LocalDate.of(2026, 3, 15), "Crédito", "CREDITAR");
        ResultadoProcessamento resultado = ResultadoProcessamento.processado(
                lancamento, new BigDecimal("1000.00"), new BigDecimal("1100.00"));

        adapter.salvar(resultado);

        ArgumentCaptor<ResultadoProcessamentoEntity> captor =
                ArgumentCaptor.forClass(ResultadoProcessamentoEntity.class);
        verify(repository).save(captor.capture());

        assertEquals("CREDITO", captor.getValue().getTipoLancamento());
        assertEquals(new BigDecimal("1000.00"), captor.getValue().getSaldoAnterior());
        assertEquals(new BigDecimal("1100.00"), captor.getValue().getSaldoPosterior());
    }
}
