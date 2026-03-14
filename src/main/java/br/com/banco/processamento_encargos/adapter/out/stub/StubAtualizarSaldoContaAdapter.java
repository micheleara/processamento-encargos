package br.com.banco.processamento_encargos.adapter.out.stub;

import br.com.banco.processamento_encargos.domain.model.TipoLancamento;
import br.com.banco.processamento_encargos.domain.port.out.AtualizarSaldoContaPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@Component
public class StubAtualizarSaldoContaAdapter implements AtualizarSaldoContaPort {

    @Override
    public void publicarAtualizacaoSaldo(String numeroConta, TipoLancamento tipo, BigDecimal valor) {
        log.debug("[STUB] Publicando atualização de saldo: conta={} tipo={} valor={}", numeroConta, tipo, valor);
    }
}

