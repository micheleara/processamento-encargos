package br.com.banco.processamento_encargos.adapter.output.repository.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "saldo_pendente")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaldoPendenteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_lancamento", nullable = false, unique = true, length = 100)
    private String idLancamento;

    @Column(name = "num_conta", nullable = false, length = 20)
    private String numConta;

    @Column(name = "tipo_lancamento", nullable = false, length = 10)
    private String tipoLancamento;

    @Column(name = "valor", nullable = false, precision = 15, scale = 2)
    private BigDecimal valor;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;
}