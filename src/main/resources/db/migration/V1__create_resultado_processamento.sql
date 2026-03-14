CREATE TABLE resultado_processamento (
    id                  BIGSERIAL PRIMARY KEY,
    id_lancamento       VARCHAR(36)    NOT NULL UNIQUE,
    numero_conta        VARCHAR(20)    NOT NULL,
    tipo_lancamento     VARCHAR(10)    NOT NULL,
    valor               NUMERIC(15,2)  NOT NULL,
    data_lancamento     DATE           NOT NULL,
    descricao           VARCHAR(255),
    status              VARCHAR(20)    NOT NULL,
    motivo_rejeicao     VARCHAR(100),
    data_processamento  TIMESTAMP      NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_resultado_numero_conta ON resultado_processamento(numero_conta);
CREATE INDEX idx_resultado_status       ON resultado_processamento(status);
CREATE INDEX idx_resultado_data         ON resultado_processamento(data_lancamento);

