package br.com.banco.processamento_encargos.adapter.output.repository;

import br.com.banco.processamento_encargos.adapter.output.repository.entity.SaldoPendenteEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SaldoPendenteJpaRepository extends JpaRepository<SaldoPendenteEntity, Long> {

    Optional<SaldoPendenteEntity> findByIdLancamento(String idLancamento);

    boolean existsByIdLancamento(String idLancamento);
}