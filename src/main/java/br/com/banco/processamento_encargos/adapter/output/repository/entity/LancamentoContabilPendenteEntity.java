package br.com.banco.processamento_encargos.adapter.output.repository.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "lancamento_contabil_pendente")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LancamentoContabilPendenteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_lancamento", nullable = false, unique = true, length = 100)
    private String idLancamento;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Setter
    @Column(name = "tentativas", nullable = false)
    private int tentativas;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Setter
    @Column(name = "ultima_tentativa")
    private LocalDateTime ultimaTentativa;
}