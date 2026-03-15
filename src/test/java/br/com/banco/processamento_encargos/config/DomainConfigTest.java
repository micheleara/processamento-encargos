package br.com.banco.processamento_encargos.config;

import br.com.banco.processamento_encargos.domain.service.ValidacaoLancamentoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DomainConfigTest {

    @Test
    @DisplayName("validacaoLancamentoService deve retornar instância não nula de ValidacaoLancamentoService")
    void deveRetornarInstanciaDeValidacaoLancamentoService() {
        DomainConfig config = new DomainConfig();

        ValidacaoLancamentoService service = config.validacaoLancamentoService();

        assertNotNull(service);
        assertInstanceOf(ValidacaoLancamentoService.class, service);
    }
}