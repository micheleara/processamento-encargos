CREATE TABLE IF NOT EXISTS resultadoprocessamentodb (
    id               BIGSERIAL PRIMARY KEY,
    id_lancamento    VARCHAR(50)     NOT NULL UNIQUE,
    num_conta        VARCHAR(20)     NOT NULL,
    tipo_lancamento  VARCHAR(10)     NOT NULL,
    valor            DECIMAL(15, 2)  NOT NULL,
    data_lancamento  DATE            NOT NULL,
    descricao        VARCHAR(200),
    evento           VARCHAR(20),
    status_proc      VARCHAR(20)     NOT NULL,
    motivo_recusa    VARCHAR(200),
    saldo_anterior   DECIMAL(15, 2),
    saldo_posterior  DECIMAL(15, 2),
    processado_em    TIMESTAMP       NOT NULL,
    CONSTRAINT resultadoprocessamentodb_check
        CHECK (
            (status_proc = 'PROCESSADO' AND motivo_recusa IS NULL)
            OR
            (status_proc = 'REJEITADO'  AND motivo_recusa IS NOT NULL)
        )
);