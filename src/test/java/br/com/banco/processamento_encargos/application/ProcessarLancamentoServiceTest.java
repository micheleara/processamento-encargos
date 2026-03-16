package br.com.banco.processamento_encargos.application;

import br.com.banco.processamento_encargos.domain.model.*;
import br.com.banco.processamento_encargos.domain.port.out.AtualizarSaldoContaPort;
import br.com.banco.processamento_encargos.domain.port.out.ConsultarClienteContaPort;
import br.com.banco.processamento_encargos.domain.port.out.SalvarResultadoProcessamentoPort;
import br.com.banco.processamento_encargos.domain.service.ValidacaoLancamentoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProcessarLancamentoServiceTest {

    @Mock
    private ConsultarClienteContaPort consultarClienteContaPort;

    @Mock
    private AtualizarSaldoContaPort atualizarSaldoContaPort;

    @Mock
    private SalvarResultadoProcessamentoPort salvarResultadoPort;

    private ValidacaoLancamentoService validacaoService;
    private ProcessarLancamentoService service;

    @BeforeEach
    void setUp() {
        validacaoService = new ValidacaoLancamentoService();
        service = new ProcessarLancamentoService(consultarClienteContaPort, atualizarSaldoContaPort, salvarResultadoPort, validacaoService);
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
        assertNull(resultado.motivoRecusa());
        verify(atualizarSaldoContaPort).publicarAtualizacaoSaldo("001234567-8", TipoLancamento.DEBITO, new BigDecimal("150.75"));
    }

    @Test
    @DisplayName("Deve retornar PROCESSADO e publicar saldo quando conta ATIVA com CREDITO")
    void deveProcessarCreditoComContaAtiva() {
        Lancamento lancamento = criarLancamento(TipoLancamento.CREDITO);
        ContaInfo conta = criarConta(StatusConta.ATIVA);
        when(consultarClienteContaPort.consultarCliente("001234567-8", "abc-123")).thenReturn(conta);

        ResultadoProcessamento resultado = service.processar(lancamento);

        assertEquals(StatusProcessamento.PROCESSADO, resultado.status());
        verify(atualizarSaldoContaPort).publicarAtualizacaoSaldo("001234567-8", TipoLancamento.CREDITO, new BigDecimal("150.75"));
    }

    @Test
    @DisplayName("Deve retornar RECUSADO quando conta CANCELADA")
    void deveRecusarQuandoContaCancelada() {
        Lancamento lancamento = criarLancamento(TipoLancamento.DEBITO);
        ContaInfo conta = criarConta(StatusConta.CANCELADA);
        when(consultarClienteContaPort.consultarCliente("001234567-8", "abc-123")).thenReturn(conta);

        ResultadoProcessamento resultado = service.processar(lancamento);

        assertEquals(StatusProcessamento.RECUSADO, resultado.status());
        assertEquals("CONTA_CANCELADA", resultado.motivoRecusa());
        verify(atualizarSaldoContaPort, never()).publicarAtualizacaoSaldo(any(), any(), any());
    }

    @Test
    @DisplayName("Deve retornar RECUSADO para DEBITO em conta com BLOQUEIO_JUDICIAL")
    void deveRecusarDebitoComBloqueioJudicial() {
        Lancamento lancamento = criarLancamento(TipoLancamento.DEBITO);
        ContaInfo conta = criarConta(StatusConta.BLOQUEIO_JUDICIAL);
        when(consultarClienteContaPort.consultarCliente("001234567-8", "abc-123")).thenReturn(conta);

        ResultadoProcessamento resultado = service.processar(lancamento);

        assertEquals(StatusProcessamento.RECUSADO, resultado.status());
        assertEquals("CONTA_COM_BLOQUEIO_JUDICIAL", resultado.motivoRecusa());
        verify(atualizarSaldoContaPort, never()).publicarAtualizacaoSaldo(any(), any(), any());
    }

    @Test
    @DisplayName("Deve retornar PROCESSADO para CREDITO em conta com BLOQUEIO_JUDICIAL")
    void deveProcessarCreditoComBloqueioJudicial() {
        Lancamento lancamento = criarLancamento(TipoLancamento.CREDITO);
        ContaInfo conta = criarConta(StatusConta.BLOQUEIO_JUDICIAL);
        when(consultarClienteContaPort.consultarCliente("001234567-8", "abc-123")).thenReturn(conta);

        ResultadoProcessamento resultado = service.processar(lancamento);

        assertEquals(StatusProcessamento.PROCESSADO, resultado.status());
        verify(atualizarSaldoContaPort).publicarAtualizacaoSaldo("001234567-8", TipoLancamento.CREDITO, new BigDecimal("150.75"));
    }

    @Test
    @DisplayName("Deve retornar RECUSADO quando conta INDISPONIVEL")
    void deveRecusarQuandoContaIndisponivel() {
        Lancamento lancamento = criarLancamento(TipoLancamento.DEBITO);
        ContaInfo conta = ContaInfo.indisponivel("001234567-8");
        when(consultarClienteContaPort.consultarCliente("001234567-8", "abc-123")).thenReturn(conta);

        ResultadoProcessamento resultado = service.processar(lancamento);

        assertEquals(StatusProcessamento.RECUSADO, resultado.status());
        assertEquals("SISTEMA_CONTAS_INDISPONIVEL", resultado.motivoRecusa());
        verify(atualizarSaldoContaPort, never()).publicarAtualizacaoSaldo(any(), any(), any());
    }

    @Test
    @DisplayName("Deve calcular saldo_posterior subtraindo valor para DEBITO")
    void deveCalcularSaldoPosteriorParaDebito() {
        Lancamento lancamento = criarLancamento(TipoLancamento.DEBITO);
        ContaInfo conta = criarConta(StatusConta.ATIVA); // saldo = 10000.00
        when(consultarClienteContaPort.consultarCliente("001234567-8", "abc-123")).thenReturn(conta);

        ResultadoProcessamento resultado = service.processar(lancamento);

        assertEquals(new BigDecimal("10000.00"), resultado.saldoAnterior());
        assertEquals(new BigDecimal("9849.25"), resultado.saldoPosterior()); // 10000 - 150.75
    }

    @Test
    @DisplayName("Deve calcular saldo_posterior somando valor para CREDITO")
    void deveCalcularSaldoPosteriorParaCredito() {
        Lancamento lancamento = criarLancamento(TipoLancamento.CREDITO);
        ContaInfo conta = criarConta(StatusConta.ATIVA); // saldo = 10000.00
        when(consultarClienteContaPort.consultarCliente("001234567-8", "abc-123")).thenReturn(conta);

        ResultadoProcessamento resultado = service.processar(lancamento);

        assertEquals(new BigDecimal("10000.00"), resultado.saldoAnterior());
        assertEquals(new BigDecimal("10150.75"), resultado.saldoPosterior()); // 10000 + 150.75
    }

    @Test
    @DisplayName("Deve retornar saldos nulos quando lançamento RECUSADO")
    void deveRetornarSaldosNulosQuandoRecusado() {
        Lancamento lancamento = criarLancamento(TipoLancamento.DEBITO);
        ContaInfo conta = criarConta(StatusConta.CANCELADA);
        when(consultarClienteContaPort.consultarCliente("001234567-8", "abc-123")).thenReturn(conta);

        ResultadoProcessamento resultado = service.processar(lancamento);

        assertNull(resultado.saldoAnterior());
        assertNull(resultado.saldoPosterior());
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
    @DisplayName("Deve preencher dados do lançamento no resultado RECUSADO")
    void devePreencherDadosNoResultadoRecusado() {
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
