package br.com.banco.processamento_encargos.adapter.out.stub;

import br.com.banco.processamento_encargos.domain.model.ContaInfo;
import br.com.banco.processamento_encargos.domain.model.StatusConta;
import br.com.banco.processamento_encargos.domain.port.out.ConsultarClienteContaPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;


@Slf4j
@Component
public class StubConsultarClienteContaAdapter implements ConsultarClienteContaPort {

    @Override
    public ContaInfo consultarCliente(String numeroConta, String correlationId) {
        log.debug("[STUB] Consultando conta {} (correlationId={}). Retornando conta ATIVA.", numeroConta, correlationId);
        return new ContaInfo(numeroConta, "Cliente Stub", StatusConta.ATIVA, new BigDecimal("10000.00"));
    }
}

