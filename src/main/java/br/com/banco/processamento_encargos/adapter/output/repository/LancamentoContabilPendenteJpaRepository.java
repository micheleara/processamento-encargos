package br.com.banco.processamento_encargos.adapter.output.repository;

import br.com.banco.processamento_encargos.adapter.output.repository.entity.LancamentoContabilPendenteEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LancamentoContabilPendenteJpaRepository extends JpaRepository<LancamentoContabilPendenteEntity, Long> {

    List<LancamentoContabilPendenteEntity> findAllByOrderByCriadoEmAsc();

    boolean existsByIdLancamento(String idLancamento);
}