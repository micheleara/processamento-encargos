package br.com.banco.processamento_encargos.domain.service;

import br.com.banco.processamento_encargos.domain.model.ContaInfo;
import br.com.banco.processamento_encargos.domain.model.Lancamento;
import br.com.banco.processamento_encargos.domain.model.StatusConta;
import br.com.banco.processamento_encargos.domain.model.TipoLancamento;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;


class ValidacaoLancamentoServiceTest {

    private ValidacaoLancamentoService service;

    @BeforeEach
    void setUp() {
        service = new ValidacaoLancamentoService();
    }

    private Lancamento criarLancamento(TipoLancamento tipo) {
        return new Lancamento(
                "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
                "001234567-8",
                tipo,
                new BigDecimal("150.75"),
                LocalDate.of(2026, 3, 10),
                "Encargos"
        );
    }

    private ContaInfo criarConta(StatusConta status) {
        return new ContaInfo("001234567-8", "João da Silva", status, new BigDecimal("10000.00"));
    }



    @Test
    @DisplayName("T1 - R1: Débito em conta com BLOQUEIO_JUDICIAL deve ser REJEITADO")
    void deveRejeitarDebitoEmContaComBloqueioJudicial() {
        Lancamento lancamento = criarLancamento(TipoLancamento.DEBITO);
        ContaInfo conta = criarConta(StatusConta.BLOQUEIO_JUDICIAL);

        Optional<String> resultado = service.validar(lancamento, conta);

        assertTrue(resultado.isPresent());
        assertEquals("CONTA_COM_BLOQUEIO_JUDICIAL", resultado.get());
    }


    @Test
    @DisplayName("T2 - R2: Crédito em conta com BLOQUEIO_JUDICIAL deve ser PROCESSADO (permitido)")
    void devePermitirCreditoEmContaComBloqueioJudicial() {
        Lancamento lancamento = criarLancamento(TipoLancamento.CREDITO);
        ContaInfo conta = criarConta(StatusConta.BLOQUEIO_JUDICIAL);

        Optional<String> resultado = service.validar(lancamento, conta);

        assertTrue(resultado.isEmpty());
    }


    @Test
    @DisplayName("T3 - R3: Débito em conta CANCELADA deve ser REJEITADO")
    void deveRejeitarDebitoEmContaCancelada() {
        Lancamento lancamento = criarLancamento(TipoLancamento.DEBITO);
        ContaInfo conta = criarConta(StatusConta.CANCELADA);

        Optional<String> resultado = service.validar(lancamento, conta);

        assertTrue(resultado.isPresent());
        assertEquals("CONTA_CANCELADA", resultado.get());
    }


    @Test
    @DisplayName("T4 - R3: Crédito em conta CANCELADA deve ser REJEITADO")
    void deveRejeitarCreditoEmContaCancelada() {
        Lancamento lancamento = criarLancamento(TipoLancamento.CREDITO);
        ContaInfo conta = criarConta(StatusConta.CANCELADA);

        Optional<String> resultado = service.validar(lancamento, conta);

        assertTrue(resultado.isPresent());
        assertEquals("CONTA_CANCELADA", resultado.get());
    }


    @Test
    @DisplayName("T5 - R5: Débito em conta ATIVA deve ser PROCESSADO")
    void devePermitirDebitoEmContaAtiva() {
        Lancamento lancamento = criarLancamento(TipoLancamento.DEBITO);
        ContaInfo conta = criarConta(StatusConta.ATIVA);

        Optional<String> resultado = service.validar(lancamento, conta);

        assertTrue(resultado.isEmpty());
    }



    @Test
    @DisplayName("T6 - R5: Crédito em conta ATIVA deve ser PROCESSADO")
    void devePermitirCreditoEmContaAtiva() {
        Lancamento lancamento = criarLancamento(TipoLancamento.CREDITO);
        ContaInfo conta = criarConta(StatusConta.ATIVA);

        Optional<String> resultado = service.validar(lancamento, conta);

        assertTrue(resultado.isEmpty());
    }


    @Test
    @DisplayName("T7 - R4: Conta INDISPONÍVEL (timeout) deve ser REJEITADO")
    void deveRejeitarQuandoContaIndisponivel() {
        Lancamento lancamento = criarLancamento(TipoLancamento.DEBITO);
        ContaInfo conta = ContaInfo.indisponivel("001234567-8");

        Optional<String> resultado = service.validar(lancamento, conta);

        assertTrue(resultado.isPresent());
        assertEquals("SISTEMA_CONTAS_INDISPONIVEL", resultado.get());
    }
}

