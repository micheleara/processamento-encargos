package br.com.banco.processamento_encargos.port.output;

import br.com.banco.processamento_encargos.core.domain.model.ContaInfo;

public interface ConsultarClienteContaOutputPort {

    ContaInfo consultarCliente(String numeroConta, String correlationId);
}
