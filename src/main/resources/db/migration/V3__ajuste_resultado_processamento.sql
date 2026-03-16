ALTER TABLE resultado_processamento RENAME COLUMN numero_conta    TO num_conta;
ALTER TABLE resultado_processamento RENAME COLUMN status          TO status_proc;
ALTER TABLE resultado_processamento RENAME COLUMN motivo_rejeicao TO motivo_recusa;

ALTER TABLE resultado_processamento ALTER COLUMN id_lancamento TYPE VARCHAR(50);
ALTER TABLE resultado_processamento ALTER COLUMN descricao     TYPE VARCHAR(200);
ALTER TABLE resultado_processamento ALTER COLUMN motivo_recusa TYPE VARCHAR(200);

ALTER TABLE resultado_processamento ADD COLUMN saldo_anterior  NUMERIC(15,2);
ALTER TABLE resultado_processamento ADD COLUMN saldo_posterior NUMERIC(15,2);

DROP INDEX IF EXISTS idx_resultado_numero_conta;
CREATE INDEX idx_resultado_num_conta ON resultado_processamento(num_conta);
