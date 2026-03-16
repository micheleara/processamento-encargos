package br.com.banco.processamento_encargos.adapter.out.kafka.dto;

import java.time.LocalDateTime;

public record ConfirmacaoContabilEvent(
        String idLancamento,
        String numLancamento,
        String status,
        LocalDateTime processadoEm
) {}