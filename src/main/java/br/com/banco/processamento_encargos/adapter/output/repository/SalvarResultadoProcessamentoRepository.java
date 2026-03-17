package br.com.banco.processamento_encargos.adapter.output.repository;

import br.com.banco.processamento_encargos.adapter.output.repository.entity.ResultadoProcessamentoEntity;
import br.com.banco.processamento_encargos.core.domain.model.ResultadoProcessamento;
import br.com.banco.processamento_encargos.core.domain.model.StatusProcessamento;
import br.com.banco.processamento_encargos.port.output.SalvarResultadoProcessamentoOutputPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
public class SalvarResultadoProcessamentoRepository implements SalvarResultadoProcessamentoOutputPort {

    private final ResultadoProcessamentoJpaRepository repository;
    private final Counter contadorProcessados;
    private final Counter contadorRejeitados;

    public SalvarResultadoProcessamentoRepository(ResultadoProcessamentoJpaRepository repository,
                                                   MeterRegistry meterRegistry) {
        this.repository = repository;
        this.contadorProcessados = Counter.builder("encargos.lancamentos.processados")
                .description("Lançamentos com status PROCESSADO persistidos")
                .register(meterRegistry);
        this.contadorRejeitados = Counter.builder("encargos.lancamentos.rejeitados")
                .description("Lançamentos com status REJEITADO persistidos")
                .register(meterRegistry);
    }

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

        if (resultado.status() == StatusProcessamento.PROCESSADO) {
            contadorProcessados.increment();
        } else {
            contadorRejeitados.increment();
        }

        log.debug("Resultado persistido id={} status={}", resultado.idLancamento(), resultado.status());
    }
}