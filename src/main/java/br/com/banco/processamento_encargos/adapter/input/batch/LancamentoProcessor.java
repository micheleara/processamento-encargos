package br.com.banco.processamento_encargos.adapter.input.batch;

import br.com.banco.processamento_encargos.core.domain.model.Lancamento;
import br.com.banco.processamento_encargos.core.domain.model.ResultadoProcessamento;
import br.com.banco.processamento_encargos.port.input.ProcessarLancamentoInputPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemProcessor;

@Slf4j
@RequiredArgsConstructor
public class LancamentoProcessor implements ItemProcessor<Lancamento, ResultadoProcessamento> {

    private final ProcessarLancamentoInputPort processarLancamentoPort;

    @Override
    public ResultadoProcessamento process(Lancamento lancamento) {
        return processarLancamentoPort.processar(lancamento);
    }
}
