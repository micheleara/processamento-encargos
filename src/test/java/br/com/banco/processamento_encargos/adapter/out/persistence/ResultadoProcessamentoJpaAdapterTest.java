package br.com.banco.processamento_encargos.adapter.out.persistence;

import br.com.banco.processamento_encargos.domain.model.Lancamento;
import br.com.banco.processamento_encargos.domain.model.ResultadoProcessamento;
import br.com.banco.processamento_encargos.domain.model.StatusProcessamento;
import br.com.banco.processamento_encargos.domain.model.TipoLancamento;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

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
                new BigDecimal("150.75"), LocalDate.of(2026, 3, 15), "Encargos mensais", "DEBITAR");
    }

    @Test
    @DisplayName("Deve mapear todos os campos corretamente para resultado PROCESSADO")
    void deveSalvarResultadoProcessadoComCamposCorretos() {
        ResultadoProcessamento resultado = ResultadoProcessamento.processado(
                criarLancamento(), new BigDecimal("1000.00"), new BigDecimal("849.25"));

        adapter.salvar(resultado);

        ArgumentCaptor<ResultadoProcessamentoEntity> captor = ArgumentCaptor.forClass(ResultadoProcessamentoEntity.class);
        verify(repository).save(captor.capture());

        ResultadoProcessamentoEntity entity = captor.getValue();
        assertEquals("TXN-001", entity.getIdLancamento());
        assertEquals("001234567-8", entity.getNumConta());
        assertEquals("DEBITO", entity.getTipoLancamento());
        assertEquals(new BigDecimal("150.75"), entity.getValor());
        assertEquals(LocalDate.of(2026, 3, 15), entity.getDataLancamento());
        assertEquals("Encargos mensais", entity.getDescricao());
        assertEquals("DEBITAR", entity.getEvento());
        assertEquals(StatusProcessamento.PROCESSADO.name(), entity.getStatusProc());
        assertNull(entity.getMotivoRecusa());
        assertEquals(new BigDecimal("1000.00"), entity.getSaldoAnterior());
        assertEquals(new BigDecimal("849.25"), entity.getSaldoPosterior());
        assertNotNull(entity.getProcessadoEm());
    }

    @Test
    @DisplayName("Deve mapear campos corretamente para resultado RECUSADO com saldos nulos")
    void deveSalvarResultadoRecusadoComSaldosNulos() {
        ResultadoProcessamento resultado = ResultadoProcessamento.rejeitado(criarLancamento(), "CONTA_CANCELADA");

        adapter.salvar(resultado);

        ArgumentCaptor<ResultadoProcessamentoEntity> captor = ArgumentCaptor.forClass(ResultadoProcessamentoEntity.class);
        verify(repository).save(captor.capture());

        ResultadoProcessamentoEntity entity = captor.getValue();
        assertEquals(StatusProcessamento.RECUSADO.name(), entity.getStatusProc());
        assertEquals("CONTA_CANCELADA", entity.getMotivoRecusa());
        assertNull(entity.getSaldoAnterior());
        assertNull(entity.getSaldoPosterior());
    }

    @Test
    @DisplayName("Deve chamar repository.save exatamente uma vez")
    void deveChamarRepositorySaveUmaVez() {
        ResultadoProcessamento resultado = ResultadoProcessamento.processado(
                criarLancamento(), new BigDecimal("500.00"), new BigDecimal("349.25"));

        adapter.salvar(resultado);

        verify(repository, times(1)).save(any(ResultadoProcessamentoEntity.class));
    }

    @Test
    @DisplayName("Deve propagar exceção lançada pelo repository")
    void devePropagarExcecaoDoRepository() {
        ResultadoProcessamento resultado = ResultadoProcessamento.processado(
                criarLancamento(), new BigDecimal("500.00"), new BigDecimal("349.25"));
        doThrow(new RuntimeException("Erro de banco")).when(repository).save(any());

        assertThrows(RuntimeException.class, () -> adapter.salvar(resultado));
    }
}