package br.com.banco.processamento_encargos.domain.port.out;

import br.com.banco.processamento_encargos.domain.model.ContaInfo;

public interface ConsultarClienteContaPort {

    ContaInfo consultarCliente(String numeroConta, String correlationId);
}

