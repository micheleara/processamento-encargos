package br.com.banco.processamento_encargos.config;

import br.com.banco.processamento_encargos.core.usecase.ProcessarLancamentoUseCase;
import br.com.banco.processamento_encargos.core.usecase.ValidacaoLancamentoUseCase;
import br.com.banco.processamento_encargos.port.output.AtualizarSaldoContaOutputPort;
import br.com.banco.processamento_encargos.port.output.ConsultarClienteContaOutputPort;
import br.com.banco.processamento_encargos.port.output.PublicarLancamentoContabilOutputPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DomainConfig {

    @Bean
    public ValidacaoLancamentoUseCase validacaoLancamentoUseCase() {
        return new ValidacaoLancamentoUseCase();
    }

    @Bean
    public ProcessarLancamentoUseCase processarLancamentoUseCase(
            ConsultarClienteContaOutputPort consultarClienteContaPort,
            AtualizarSaldoContaOutputPort atualizarSaldoContaPort,
            PublicarLancamentoContabilOutputPort publicarLancamentoContabilPort,
            ValidacaoLancamentoUseCase validacaoService) {
        return new ProcessarLancamentoUseCase(consultarClienteContaPort, atualizarSaldoContaPort, publicarLancamentoContabilPort, validacaoService);
    }
}

