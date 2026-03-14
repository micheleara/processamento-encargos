package br.com.banco.processamento_encargos.adapter.in.batch;

import br.com.banco.processamento_encargos.domain.model.Lancamento;
import br.com.banco.processamento_encargos.domain.model.ResultadoProcessamento;
import br.com.banco.processamento_encargos.domain.port.in.ProcessarLancamentoPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemProcessor;

@Slf4j
@RequiredArgsConstructor
public class LancamentoProcessor implements ItemProcessor<Lancamento, ResultadoProcessamento> {

    private final ProcessarLancamentoPort processarLancamentoPort;

    @Override
    public ResultadoProcessamento process(Lancamento lancamento) {
        return processarLancamentoPort.processar(lancamento);
    }
}

