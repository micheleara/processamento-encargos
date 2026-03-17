package br.com.banco.processamento_encargos.core.usecase;

import br.com.banco.processamento_encargos.core.domain.model.*;
import br.com.banco.processamento_encargos.port.output.AtualizarSaldoContaOutputPort;
import br.com.banco.processamento_encargos.port.output.ConsultarClienteContaOutputPort;
import br.com.banco.processamento_encargos.port.output.PublicarLancamentoContabilOutputPort;
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
class ProcessarLancamentoUseCaseTest {

    @Mock
    private ConsultarClienteContaOutputPort consultarClienteContaPort;

    @Mock
    private AtualizarSaldoContaOutputPort atualizarSaldoContaPort;

    @Mock
    private PublicarLancamentoContabilOutputPort publicarLancamentoContabilPort;

    private ValidacaoLancamentoUseCase validacaoService;
    private ProcessarLancamentoUseCase service;

    @BeforeEach
    void setUp() {
        validacaoService = new ValidacaoLancamentoUseCase();
        service = new ProcessarLancamentoUseCase(consultarClienteContaPort, atualizarSaldoContaPort, publicarLancamentoContabilPort, validacaoService);
    }

    private Lancamento criarLancamento(TipoLancamento tipo) {
        return new Lancamento(
                "abc-123", "001234567-8", tipo,
                new BigDecimal("150.75"), LocalDate.of(2026, 3, 10), "Encargos", "Debitar");
    }

    private ContaInfo criarConta(StatusConta status) {
        return new ContaInfo("001234567-8", "João da Silva", status, new BigDecimal("10000.00"));
    }

    @Test
    @DisplayName("Deve retornar PROCESSADO e publicar saldo quando conta ATIVA com DEBITO")
    void deveProcessarDebitoComContaAtiva() {
        Lancamento lancamento = criarLancamento(TipoLancamento.DEBITO);
        ContaInfo conta = criarConta(StatusConta.ATIVA);
        when(consultarClienteContaPort.consultarCliente("001234567-8", "abc-123")).thenReturn(conta);

        ResultadoProcessamento resultado = service.processar(lancamento);

        assertEquals(StatusProcessamento.PROCESSADO, resultado.status());
        assertNull(resultado.motivoRejeicao());
        assertEquals(new BigDecimal("10000.00"), resultado.saldoAnterior());
        assertEquals(new BigDecimal("9849.25"), resultado.saldoPosterior());
        verify(atualizarSaldoContaPort).publicarAtualizacaoSaldo("abc-123", "001234567-8", TipoLancamento.DEBITO, new BigDecimal("150.75"));
        verify(publicarLancamentoContabilPort).publicar(resultado);
    }

    @Test
    @DisplayName("Deve retornar PROCESSADO e publicar saldo quando conta ATIVA com CREDITO")
    void deveProcessarCreditoComContaAtiva() {
        Lancamento lancamento = criarLancamento(TipoLancamento.CREDITO);
        ContaInfo conta = criarConta(StatusConta.ATIVA);
        when(consultarClienteContaPort.consultarCliente("001234567-8", "abc-123")).thenReturn(conta);

        ResultadoProcessamento resultado = service.processar(lancamento);

        assertEquals(StatusProcessamento.PROCESSADO, resultado.status());
        assertEquals(new BigDecimal("10000.00"), resultado.saldoAnterior());
        assertEquals(new BigDecimal("10150.75"), resultado.saldoPosterior());
        verify(atualizarSaldoContaPort).publicarAtualizacaoSaldo("abc-123", "001234567-8", TipoLancamento.CREDITO, new BigDecimal("150.75"));
        verify(publicarLancamentoContabilPort).publicar(resultado);
    }

    @Test
    @DisplayName("Deve retornar REJEITADO quando conta CANCELADA")
    void deveRejeitarQuandoContaCancelada() {
        Lancamento lancamento = criarLancamento(TipoLancamento.DEBITO);
        ContaInfo conta = criarConta(StatusConta.CANCELADA);
        when(consultarClienteContaPort.consultarCliente("001234567-8", "abc-123")).thenReturn(conta);

        ResultadoProcessamento resultado = service.processar(lancamento);

        assertEquals(StatusProcessamento.REJEITADO, resultado.status());
        assertEquals("CONTA_CANCELADA", resultado.motivoRejeicao());
        verify(atualizarSaldoContaPort, never()).publicarAtualizacaoSaldo(any(), any(), any(), any());
        verify(publicarLancamentoContabilPort, never()).publicar(any());
    }

    @Test
    @DisplayName("Deve retornar REJEITADO para DEBITO em conta com BLOQUEIO_JUDICIAL")
    void deveRejeitarDebitoComBloqueioJudicial() {
        Lancamento lancamento = criarLancamento(TipoLancamento.DEBITO);
        ContaInfo conta = criarConta(StatusConta.BLOQUEIO_JUDICIAL);
        when(consultarClienteContaPort.consultarCliente("001234567-8", "abc-123")).thenReturn(conta);

        ResultadoProcessamento resultado = service.processar(lancamento);

        assertEquals(StatusProcessamento.REJEITADO, resultado.status());
        assertEquals("CONTA_COM_BLOQUEIO_JUDICIAL", resultado.motivoRejeicao());
        verify(atualizarSaldoContaPort, never()).publicarAtualizacaoSaldo(any(), any(), any(), any());
        verify(publicarLancamentoContabilPort, never()).publicar(any());
    }

    @Test
    @DisplayName("Deve retornar PROCESSADO para CREDITO em conta com BLOQUEIO_JUDICIAL")
    void deveProcessarCreditoComBloqueioJudicial() {
        Lancamento lancamento = criarLancamento(TipoLancamento.CREDITO);
        ContaInfo conta = criarConta(StatusConta.BLOQUEIO_JUDICIAL);
        when(consultarClienteContaPort.consultarCliente("001234567-8", "abc-123")).thenReturn(conta);

        ResultadoProcessamento resultado = service.processar(lancamento);

        assertEquals(StatusProcessamento.PROCESSADO, resultado.status());
        verify(atualizarSaldoContaPort).publicarAtualizacaoSaldo("abc-123", "001234567-8", TipoLancamento.CREDITO, new BigDecimal("150.75"));
        verify(publicarLancamentoContabilPort).publicar(resultado);
    }

    @Test
    @DisplayName("Deve retornar REJEITADO quando conta INDISPONIVEL")
    void deveRejeitarQuandoContaIndisponivel() {
        Lancamento lancamento = criarLancamento(TipoLancamento.DEBITO);
        ContaInfo conta = ContaInfo.indisponivel("001234567-8");
        when(consultarClienteContaPort.consultarCliente("001234567-8", "abc-123")).thenReturn(conta);

        ResultadoProcessamento resultado = service.processar(lancamento);

        assertEquals(StatusProcessamento.REJEITADO, resultado.status());
        assertEquals("SISTEMA_CONTAS_INDISPONIVEL", resultado.motivoRejeicao());
        verify(atualizarSaldoContaPort, never()).publicarAtualizacaoSaldo(any(), any(), any(), any());
        verify(publicarLancamentoContabilPort, never()).publicar(any());
    }

    @Test
    @DisplayName("Deve preencher dados do lançamento no resultado PROCESSADO")
    void devePreencherDadosNoResultadoProcessado() {
        Lancamento lancamento = criarLancamento(TipoLancamento.DEBITO);
        ContaInfo conta = criarConta(StatusConta.ATIVA);
        when(consultarClienteContaPort.consultarCliente("001234567-8", "abc-123")).thenReturn(conta);

        ResultadoProcessamento resultado = service.processar(lancamento);

        assertEquals("abc-123", resultado.idLancamento());
        assertEquals("001234567-8", resultado.numeroConta());
        assertEquals(TipoLancamento.DEBITO, resultado.tipoLancamento());
        assertEquals(new BigDecimal("150.75"), resultado.valor());
        assertEquals(LocalDate.of(2026, 3, 10), resultado.dataLancamento());
        assertEquals("Encargos", resultado.descricao());
        assertNotNull(resultado.dataProcessamento());
    }

    @Test
    @DisplayName("Deve preencher dados do lançamento no resultado REJEITADO")
    void devePreencherDadosNoResultadoRejeitado() {
        Lancamento lancamento = criarLancamento(TipoLancamento.DEBITO);
        ContaInfo conta = criarConta(StatusConta.CANCELADA);
        when(consultarClienteContaPort.consultarCliente("001234567-8", "abc-123")).thenReturn(conta);

        ResultadoProcessamento resultado = service.processar(lancamento);

        assertEquals("abc-123", resultado.idLancamento());
        assertEquals("001234567-8", resultado.numeroConta());
        assertEquals(TipoLancamento.DEBITO, resultado.tipoLancamento());
        assertNotNull(resultado.dataProcessamento());
    }
}