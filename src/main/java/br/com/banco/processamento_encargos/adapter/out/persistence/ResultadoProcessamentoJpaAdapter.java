package br.com.banco.processamento_encargos.adapter.out.persistence;

import br.com.banco.processamento_encargos.domain.model.ResultadoProcessamento;
import br.com.banco.processamento_encargos.domain.port.out.SalvarResultadoProcessamentoPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ResultadoProcessamentoJpaAdapter implements SalvarResultadoProcessamentoPort {

    private final ResultadoProcessamentoRepository repository;

    @Override
    public void salvar(ResultadoProcessamento resultado) {
        ResultadoProcessamentoEntity entity = ResultadoProcessamentoEntity.builder()
                .idLancamento(resultado.idLancamento())
                .numeroConta(resultado.numeroConta())
                .tipoLancamento(resultado.tipoLancamento().name())
                .valor(resultado.valor())
                .dataLancamento(resultado.dataLancamento())
                .descricao(resultado.descricao())
                .evento(resultado.evento())
                .status(resultado.status().name())
                .motivoRejeicao(resultado.motivoRejeicao())
                .dataProcessamento(resultado.dataProcessamento())
                .build();

        repository.save(entity);
        log.debug("Resultado persistido id={} status={}", resultado.idLancamento(), resultado.status());
    }
}