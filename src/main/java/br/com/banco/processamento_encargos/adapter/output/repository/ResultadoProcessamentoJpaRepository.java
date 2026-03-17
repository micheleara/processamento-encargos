package br.com.banco.processamento_encargos.adapter.output.repository;

import br.com.banco.processamento_encargos.adapter.output.repository.entity.ResultadoProcessamentoEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResultadoProcessamentoJpaRepository extends JpaRepository<ResultadoProcessamentoEntity, Long> {

    boolean existsByIdLancamento(String idLancamento);
}