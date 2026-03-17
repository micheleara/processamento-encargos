package br.com.banco.processamento_encargos.core.usecase;

import br.com.banco.processamento_encargos.core.domain.model.ContaInfo;
import br.com.banco.processamento_encargos.core.domain.model.Lancamento;
import br.com.banco.processamento_encargos.core.domain.model.StatusConta;
import br.com.banco.processamento_encargos.core.domain.model.TipoLancamento;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;


class ValidacaoLancamentoUseCaseTest {

    private ValidacaoLancamentoUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ValidacaoLancamentoUseCase();
    }

    private Lancamento criarLancamento(TipoLancamento tipo) {
        return new Lancamento(
                "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
                "001234567-8",
                tipo,
                new BigDecimal("150.75"),
                LocalDate.of(2026, 3, 10),
                "Encargos",
                "Debitar"
        );
    }

    private ContaInfo criarConta(StatusConta status) {
        return new ContaInfo("001234567-8", "João da Silva", status, new BigDecimal("10000.00"));
    }

    @Test
    @DisplayName("T1 - R1: Débito em conta com BLOQUEIO_JUDICIAL deve ser REJEITADO")
    void deveRejeitarDebitoEmContaComBloqueioJudicial() {
        Optional<String> resultado = useCase.validar(criarLancamento(TipoLancamento.DEBITO), criarConta(StatusConta.BLOQUEIO_JUDICIAL));

        assertTrue(resultado.isPresent());
        assertEquals("CONTA_COM_BLOQUEIO_JUDICIAL", resultado.get());
    }

    @Test
    @DisplayName("T2 - R2: Crédito em conta com BLOQUEIO_JUDICIAL deve ser PROCESSADO (permitido)")
    void devePermitirCreditoEmContaComBloqueioJudicial() {
        Optional<String> resultado = useCase.validar(criarLancamento(TipoLancamento.CREDITO), criarConta(StatusConta.BLOQUEIO_JUDICIAL));

        assertTrue(resultado.isEmpty());
    }

    @Test
    @DisplayName("T3 - R3: Débito em conta CANCELADA deve ser REJEITADO")
    void deveRejeitarDebitoEmContaCancelada() {
        Optional<String> resultado = useCase.validar(criarLancamento(TipoLancamento.DEBITO), criarConta(StatusConta.CANCELADA));

        assertTrue(resultado.isPresent());
        assertEquals("CONTA_CANCELADA", resultado.get());
    }

    @Test
    @DisplayName("T4 - R3: Crédito em conta CANCELADA deve ser REJEITADO")
    void deveRejeitarCreditoEmContaCancelada() {
        Optional<String> resultado = useCase.validar(criarLancamento(TipoLancamento.CREDITO), criarConta(StatusConta.CANCELADA));

        assertTrue(resultado.isPresent());
        assertEquals("CONTA_CANCELADA", resultado.get());
    }

    @Test
    @DisplayName("T5 - R5: Débito em conta ATIVA deve ser PROCESSADO")
    void devePermitirDebitoEmContaAtiva() {
        Optional<String> resultado = useCase.validar(criarLancamento(TipoLancamento.DEBITO), criarConta(StatusConta.ATIVA));

        assertTrue(resultado.isEmpty());
    }

    @Test
    @DisplayName("T6 - R5: Crédito em conta ATIVA deve ser PROCESSADO")
    void devePermitirCreditoEmContaAtiva() {
        Optional<String> resultado = useCase.validar(criarLancamento(TipoLancamento.CREDITO), criarConta(StatusConta.ATIVA));

        assertTrue(resultado.isEmpty());
    }

    @Test
    @DisplayName("T7 - R4: Conta INDISPONÍVEL (timeout) deve ser REJEITADO")
    void deveRejeitarQuandoContaIndisponivel() {
        Optional<String> resultado = useCase.validar(criarLancamento(TipoLancamento.DEBITO), ContaInfo.indisponivel("001234567-8"));

        assertTrue(resultado.isPresent());
        assertEquals("SISTEMA_CONTAS_INDISPONIVEL", resultado.get());
    }
}