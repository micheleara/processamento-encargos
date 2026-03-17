package br.com.banco.processamento_encargos.adapter.out.persistence;

import br.com.banco.processamento_encargos.domain.model.ResultadoProcessamento;
import br.com.banco.processamento_encargos.domain.port.out.SalvarResultadoProcessamentoPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class ResultadoProcessamentoJpaAdapter implements SalvarResultadoProcessamentoPort {

    private final ResultadoProcessamentoRepository repository;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void salvar(ResultadoProcessamento resultado) {
        if (repository.existsByIdLancamento(resultado.idLancamento())) {
            log.warn("idLancamento já existe na base, ignorando: id={}", resultado.idLancamento());
            return;
        }

        ResultadoProcessamentoEntity entity = ResultadoProcessamentoEntity.builder()
                .idLancamento(resultado.idLancamento())
                .numConta(resultado.numeroConta())
                .tipoLancamento(resultado.tipoLancamento().name())
                .valor(resultado.valor())
                .dataLancamento(resultado.dataLancamento())
                .descricao(resultado.descricao())
                .evento(resultado.evento())
                .statusProc(resultado.status().name())
                .motivoRecusa(resultado.motivoRejeicao())
                .saldoAnterior(resultado.saldoAnterior())
                .saldoPosterior(resultado.saldoPosterior())
                .processadoEm(resultado.dataProcessamento())
                .build();

        repository.save(entity);
        log.debug("Resultado persistido id={} status={}", resultado.idLancamento(), resultado.status());
    }
}