package br.com.banco.processamento_encargos.config;

import br.com.banco.processamento_encargos.core.usecase.ProcessarLancamentoUseCase;
import br.com.banco.processamento_encargos.core.usecase.ValidacaoLancamentoUseCase;
import br.com.banco.processamento_encargos.port.output.AtualizarSaldoContaOutputPort;
import br.com.banco.processamento_encargos.port.output.ConsultarClienteContaOutputPort;
import br.com.banco.processamento_encargos.port.output.PublicarLancamentoContabilOutputPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class DomainConfigTest {

    @Mock
    private ConsultarClienteContaOutputPort consultarClienteContaPort;

    @Mock
    private AtualizarSaldoContaOutputPort atualizarSaldoContaPort;

    @Mock
    private PublicarLancamentoContabilOutputPort publicarLancamentoContabilPort;

    @Test
    @DisplayName("validacaoLancamentoUseCase deve retornar instância não nula de ValidacaoLancamentoUseCase")
    void deveRetornarInstanciaDeValidacaoLancamentoUseCase() {
        DomainConfig config = new DomainConfig();

        ValidacaoLancamentoUseCase useCase = config.validacaoLancamentoUseCase();

        assertNotNull(useCase);
        assertInstanceOf(ValidacaoLancamentoUseCase.class, useCase);
    }

    @Test
    @DisplayName("processarLancamentoUseCase deve retornar instância não nula de ProcessarLancamentoUseCase")
    void deveRetornarInstanciaDeProcessarLancamentoUseCase() {
        DomainConfig config = new DomainConfig();
        ValidacaoLancamentoUseCase validacaoService = config.validacaoLancamentoUseCase();

        ProcessarLancamentoUseCase useCase = config.processarLancamentoUseCase(
                consultarClienteContaPort, atualizarSaldoContaPort, publicarLancamentoContabilPort, validacaoService);

        assertNotNull(useCase);
        assertInstanceOf(ProcessarLancamentoUseCase.class, useCase);
    }
}