package br.com.banco.processamento_encargos.adapter.output.repository.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "resultadoprocessamentodb")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResultadoProcessamentoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_lancamento", nullable = false, unique = true, length = 50)
    private String idLancamento;

    @Column(name = "num_conta", nullable = false, length = 20)
    private String numConta;

    @Column(name = "tipo_lancamento", nullable = false, length = 10)
    private String tipoLancamento;

    @Column(name = "valor", nullable = false, precision = 15, scale = 2)
    private BigDecimal valor;

    @Column(name = "data_lancamento", nullable = false)
    private LocalDate dataLancamento;

    @Column(name = "descricao", length = 200)
    private String descricao;

    @Column(name = "evento", length = 20)
    private String evento;

    @Column(name = "status_proc", nullable = false, length = 20)
    private String statusProc;

    @Column(name = "motivo_recusa", length = 200)
    private String motivoRecusa;

    @Column(name = "saldo_anterior", precision = 15, scale = 2)
    private BigDecimal saldoAnterior;

    @Column(name = "saldo_posterior", precision = 15, scale = 2)
    private BigDecimal saldoPosterior;

    @Column(name = "processado_em", nullable = false)
    private LocalDateTime processadoEm;
}