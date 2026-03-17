package br.com.banco.processamento_encargos.adapter.input.batch;

import br.com.banco.processamento_encargos.core.domain.model.Lancamento;
import br.com.banco.processamento_encargos.core.domain.model.ResultadoProcessamento;
import br.com.banco.processamento_encargos.core.domain.model.StatusProcessamento;
import br.com.banco.processamento_encargos.core.domain.model.TipoLancamento;
import br.com.banco.processamento_encargos.port.input.ProcessarLancamentoInputPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LancamentoProcessorTest {

    @Mock
    private ProcessarLancamentoInputPort processarLancamentoPort;

    private LancamentoProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new LancamentoProcessor(processarLancamentoPort);
    }

    private Lancamento criarLancamento() {
        return new Lancamento(
                "abc-123", "001234567-8", TipoLancamento.DEBITO,
                new BigDecimal("150.75"), LocalDate.of(2026, 3, 10), "Taxa mensal", "Debitar");
    }

    @Test
    @DisplayName("Deve delegar processamento para o port e retornar resultado PROCESSADO")
    void deveDelegarProcessamentoERetornarProcessado() {
        Lancamento lancamento = criarLancamento();
        ResultadoProcessamento esperado = ResultadoProcessamento.processado(
                lancamento, new BigDecimal("10000.00"), new BigDecimal("9849.25"));
        when(processarLancamentoPort.processar(lancamento)).thenReturn(esperado);

        ResultadoProcessamento resultado = processor.process(lancamento);

        assertNotNull(resultado);
        assertEquals(StatusProcessamento.PROCESSADO, resultado.status());
        assertEquals("abc-123", resultado.idLancamento());
        verify(processarLancamentoPort, times(1)).processar(lancamento);
    }

    @Test
    @DisplayName("Deve delegar processamento para o port e retornar resultado REJEITADO")
    void deveDelegarProcessamentoERetornarRejeitado() {
        Lancamento lancamento = criarLancamento();
        ResultadoProcessamento esperado = ResultadoProcessamento.rejeitado(lancamento, "CONTA_CANCELADA");
        when(processarLancamentoPort.processar(lancamento)).thenReturn(esperado);

        ResultadoProcessamento resultado = processor.process(lancamento);

        assertNotNull(resultado);
        assertEquals(StatusProcessamento.REJEITADO, resultado.status());
        assertEquals("CONTA_CANCELADA", resultado.motivoRejeicao());
        verify(processarLancamentoPort, times(1)).processar(lancamento);
    }

    @Test
    @DisplayName("Deve propagar exceção lançada pelo port")
    void devePropagarExcecao() {
        Lancamento lancamento = criarLancamento();
        when(processarLancamentoPort.processar(lancamento))
                .thenThrow(new RuntimeException("Erro inesperado"));

        assertThrows(RuntimeException.class, () -> processor.process(lancamento));
        verify(processarLancamentoPort, times(1)).processar(lancamento);
    }
}
