package br.com.banco.processamento_encargos.adapter.out.persistence;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "resultado_processamento")
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

    @Column(name = "numero_conta", nullable = false, length = 20)
    private String numeroConta;

    @Column(name = "tipo_lancamento", nullable = false, length = 10)
    private String tipoLancamento;

    @Column(name = "valor", nullable = false, precision = 15, scale = 2)
    private BigDecimal valor;

    @Column(name = "data_lancamento", nullable = false)
    private LocalDate dataLancamento;

    @Column(name = "descricao", length = 255)
    private String descricao;

    @Column(name = "evento", length = 50)
    private String evento;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "motivo_rejeicao", length = 100)
    private String motivoRejeicao;

    @Column(name = "data_processamento", nullable = false)
    private LocalDateTime dataProcessamento;
}