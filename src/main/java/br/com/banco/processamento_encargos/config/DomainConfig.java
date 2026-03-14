package br.com.banco.processamento_encargos.config;

import br.com.banco.processamento_encargos.domain.service.ValidacaoLancamentoService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DomainConfig {

    @Bean
    public ValidacaoLancamentoService validacaoLancamentoService() {
        return new ValidacaoLancamentoService();
    }
}

