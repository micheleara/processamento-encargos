-- Tabela de saldo pendente: substitui o ConcurrentHashMap em memória.
-- Persiste o evento de atualização de saldo aguardando confirmação contábil,
-- garantindo durabilidade mesmo em caso de restart ou múltiplas instâncias.
CREATE TABLE saldo_pendente (
    id              BIGSERIAL PRIMARY KEY,
    id_lancamento   VARCHAR(100)   NOT NULL UNIQUE,
    num_conta       VARCHAR(20)    NOT NULL,
    tipo_lancamento VARCHAR(10)    NOT NULL,
    valor           DECIMAL(15, 2) NOT NULL,
    criado_em       TIMESTAMP      NOT NULL DEFAULT NOW()
);

-- Tabela outbox para eventos contábeis que falharam na publicação Kafka.
-- Um scheduler lê e re-tenta a publicação até o evento ser processado.
CREATE TABLE lancamento_contabil_pendente (
    id               BIGSERIAL PRIMARY KEY,
    id_lancamento    VARCHAR(100) NOT NULL UNIQUE,
    payload          TEXT         NOT NULL,
    tentativas       INT          NOT NULL DEFAULT 0,
    criado_em        TIMESTAMP    NOT NULL DEFAULT NOW(),
    ultima_tentativa TIMESTAMP
);