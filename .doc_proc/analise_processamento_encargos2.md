# Análise do Projeto — Processamento de Encargos em Conta Corrente

> **Data da análise:** 2026-03-14
> **Base:** Código-fonte atual + `PLANO_DESENVOLVIMENTO_PROCESSAMENTO_ENCARGOS.md`
> **Objetivo:** Mapear o que foi implementado, o que está pendente e as inconsistências encontradas

---

## Sumário

1. [Visão Geral do Projeto](#1-visão-geral-do-projeto)
2. [Stack Tecnológica Confirmada](#2-stack-tecnológica-confirmada)
3. [O que está implementado](#3-o-que-está-implementado)
4. [O que está pendente (Stubs e Lacunas)](#4-o-que-está-pendente-stubs-e-lacunas)
5. [Inconsistências e Código Morto](#5-inconsistências-e-código-morto)
6. [Plano de Ação — Próximas Entregas](#6-plano-de-ação--próximas-entregas)
7. [Riscos Identificados](#7-riscos-identificados)

---

## 1. Visão Geral do Projeto

Sistema batch responsável pelo **processamento diário de encargos em contas correntes**. Lê um arquivo CSV com até 20 milhões de lançamentos do S3, valida regras de negócio, comunica-se com o Sistema de Contas externo (via Kafka) e persiste os resultados em PostgreSQL, expondo-os via API REST.

### Diagrama de componentes atual

```
┌─────────────────────────────────────────────────────────────┐
│              PROCESSADOR DE ENCARGOS (este sistema)          │
│                                                              │
│  [Scheduler 04:00] ──► [BatchJobConfig]                     │
│                              │                               │
│                    ┌─────────▼──────────┐                   │
│                    │  FlatFileItemReader  │ ◄── S3 Stream    │
│                    │  (CSV 20M linhas)    │                  │
│                    └─────────┬──────────┘                   │
│                              │ chunks                        │
│                    ┌─────────▼──────────┐                   │
│                    │ LancamentoProcessor  │                  │
│                    │  └► ProcessarLanc.  │                  │
│                    │      Service        │                  │
│                    └─────────┬──────────┘                   │
│                              │                               │
│              ┌───────────────┼───────────────┐              │
│              ▼               ▼               ▼              │
│     [STUB: Consultar   [STUB: Atualizar  [JdbcBatchWriter]  │
│      ClienteConta]      SaldoConta]      (PostgreSQL)       │
│     (devia ser Kafka)  (devia ser Kafka)                    │
│                                                              │
│  [JobController POST /api/v1/batch/executar]                │
│  [Actuator + Prometheus /actuator/prometheus]               │
└─────────────────────────────────────────────────────────────┘
```

---

## 2. Stack Tecnológica Confirmada

| Componente | Tecnologia | Versão |
|------------|------------|--------|
| Linguagem | Java | 21 (Virtual Threads habilitadas) |
| Framework | Spring Boot | 3.5.11 |
| Batch | Spring Batch | via spring-boot-starter-batch |
| Eventos | Apache Kafka | Confluent 7.6.0 (docker-compose) |
| Banco de dados | PostgreSQL | 16 |
| Migrations | Flyway | via flyway-database-postgresql |
| ORM | Spring Data JPA + Hibernate | ddl-auto: validate |
| Armazenamento | AWS S3 | SDK 2.25.14 |
| Métricas | Micrometer + Prometheus | via actuator |
| Build | Maven + JaCoCo | cobertura mínima 80% |
| Utilitários | Lombok | |
| Testes | JUnit 5, Mockito, spring-batch-test, spring-kafka-test, H2 | |

---

## 3. O que está implementado

### 3.1 Camada de Domínio ✅ Completo

| Classe | Tipo | Status |
|--------|------|--------|
| `Lancamento` | record (imutável) | ✅ Implementado |
| `ContaInfo` | record (imutável) | ✅ Implementado |
| `ResultadoProcessamento` | record com factory methods | ✅ Implementado |
| `StatusConta` | enum: ATIVA, CANCELADA, BLOQUEIO_JUDICIAL, INDISPONIVEL | ✅ Implementado |
| `StatusProcessamento` | enum: PROCESSADO, REJEITADO | ✅ Implementado |
| `TipoLancamento` | enum: DEBITO, CREDITO | ✅ Implementado |
| `ValidacaoLancamentoService` | regras puras sem dependências | ✅ Implementado |
| `ProcessarLancamentoPort` (in) | interface | ✅ Implementado |
| `ConsultarResultadoProcessamentoPort` (in) | interface | ✅ Declarada — **sem implementação** |
| `AtualizarSaldoContaPort` (out) | interface | ✅ Implementado |
| `ConsultarClienteContaPort` (out) | interface | ✅ Implementado |
| `PersistirResultadoPort` (out) | interface | ✅ Declarada — **stub não utilizado** |

**Regras de negócio implementadas em `ValidacaoLancamentoService`:**
- `SISTEMA_CONTAS_INDISPONIVEL` → conta com status `INDISPONIVEL`
- `CONTA_CANCELADA` → conta com status `CANCELADA`
- `CONTA_COM_BLOQUEIO_JUDICIAL` → débito em conta com `BLOQUEIO_JUDICIAL`
- Créditos em conta com `BLOQUEIO_JUDICIAL` são **aprovados** (regra correta)

### 3.2 Camada de Aplicação ✅

- `ProcessarLancamentoService`: orquestra consulta de conta → validação → atualização de saldo → retorno do resultado. Fluxo correto e bem estruturado.

### 3.3 Adapter Batch (Entrada) ✅ Parcialmente

| Componente | Status | Observação |
|------------|--------|------------|
| `BatchJobConfig` | ✅ | chunk-size configurável, multi-thread com Virtual Threads |
| `LancamentoCsvFieldSetMapper` | ✅ | mapeia campos do CSV para `Lancamento` |
| `LancamentoProcessor` | ✅ | delega para `ProcessarLancamentoPort` |
| `EncargosJobScheduler` | ✅ | cron `0 0 4 * * *` (04:00 diário) |
| `SynchronizedItemStreamReader` | ✅ | thread-safe para leitura paralela |
| `S3FileDownloadAdapter.abrirStreamArquivoDoDia()` | ✅ | stream direto S3, sem download para disco |
| `LancamentoPartitioner` | ⚠️ | implementado mas **não conectado** ao BatchJobConfig |
| `JdbcBatchItemWriter` | ✅ | INSERT com `ON CONFLICT DO NOTHING` (idempotente) |

**Configuração do batch:**
```yaml
encargos.batch.chunk-size: 1000    # padrão
encargos.batch.partitions: 10      # padrão (usado como concurrencyLimit)
```

**Virtual Threads habilitadas:**
```java
executor.setVirtualThreads(true); // Java 21
```

### 3.4 Adapter S3 (Saída) ✅

- `S3ClientConfig`: configura `S3Client` com credenciais via variáveis de ambiente
- `S3FileDownloadAdapter`: abre stream direto do S3 sem salvar em disco
- `S3Exception`: exceção tipada para erros S3
- `S3Properties`: mapeamento de propriedades `cloud.aws.s3.*`

### 3.5 Adapter REST (Entrada) ⚠️ Parcial

| Endpoint | Método | Status |
|----------|--------|--------|
| `POST /api/v1/batch/executar` | Trigger manual do job | ✅ Implementado |
| `GET /api/v1/lancamentos` | Consulta resultados | ❌ **Não existe** |
| `GET /api/v1/lancamentos/{id}` | Consulta por ID | ❌ **Não existe** |
| `GET /api/v1/lancamentos?conta=...` | Consulta por conta | ❌ **Não existe** |

### 3.6 Persistência ✅

Migration `V1__create_resultado_processamento.sql`:
```sql
CREATE TABLE resultado_processamento (
    id                  BIGSERIAL PRIMARY KEY,
    id_lancamento       VARCHAR(36)    NOT NULL UNIQUE,  -- garante idempotência
    numero_conta        VARCHAR(20)    NOT NULL,
    tipo_lancamento     VARCHAR(10)    NOT NULL,
    valor               NUMERIC(15,2)  NOT NULL,
    data_lancamento     DATE           NOT NULL,
    descricao           VARCHAR(255),
    status              VARCHAR(20)    NOT NULL,
    motivo_rejeicao     VARCHAR(100),
    data_processamento  TIMESTAMP      NOT NULL DEFAULT NOW()
);
-- Índices para consulta via API
CREATE INDEX idx_resultado_numero_conta ON resultado_processamento(numero_conta);
CREATE INDEX idx_resultado_status       ON resultado_processamento(status);
CREATE INDEX idx_resultado_data         ON resultado_processamento(data_lancamento);
```

### 3.7 Infraestrutura ✅

- `docker-compose.yml`: PostgreSQL 16 + Kafka (Confluent 7.6.0 + Zookeeper)
- Actuator exposto: `health`, `metrics`, `prometheus`, `info`
- JaCoCo configurado: cobertura mínima de 80% por linhas

### 3.8 Testes ✅ Boa cobertura unitária

| Classe de Teste | Componente Testado |
|-----------------|--------------------|
| `ValidacaoLancamentoServiceTest` | Regras de negócio do domínio |
| `ProcessarLancamentoServiceTest` | Orquestração do application service |
| `LancamentoCsvFieldSetMapperTest` | Mapeamento do CSV |
| `LancamentoPartitionerTest` | Lógica de particionamento |
| `LancamentoProcessorTest` | Processor do batch |
| `S3DownloadTaskletTest` | Tasklet de download S3 |
| `S3FileDownloadAdapterTest` | Adapter S3 |
| `S3ExceptionTest` | Exception customizada |
| `StubAdaptersTest` | Stubs de saída |
| `JobControllerTest` | Controller de trigger |
| `EncargosJobSchedulerTest` | Scheduler |
| `ContaInfoTest`, `ResultadoProcessamentoTest` | Modelos de domínio |

---

## 4. O que está pendente (Stubs e Lacunas)

### 4.1 ❌ Adapter Kafka — `ConsultarClienteContaPort`

**Situação:** `StubConsultarClienteContaAdapter` retorna sempre `ATIVA` com saldo R$ 10.000,00.

**O que precisa ser implementado:**
- Publicar evento no tópico Kafka de request (ex: `encargos.conta.consulta.request`)
- Aguardar resposta no tópico Kafka de response (ex: `encargos.conta.consulta.response`)
- Pattern a usar: `ReplyingKafkaTemplate` (request-reply síncrono) ou correlationId com consumer separado
- Deserialização de `ContaInfo` a partir do payload JSON da resposta

**Contrato esperado do Sistema de Contas (response):**
```json
{
  "correlationId": "uuid-do-lancamento",
  "numeroConta": "12345-6",
  "nomeCliente": "João da Silva",
  "status": "ATIVA",
  "saldo": 5000.00
}
```

**Impacto:** Sem isso, todos os lançamentos são aprovados independente do status da conta.

---

### 4.2 ❌ Adapter Kafka — `AtualizarSaldoContaPort`

**Situação:** `StubAtualizarSaldoContaAdapter` apenas loga, não publica nada.

**O que precisa ser implementado:**
- Publicar evento no tópico Kafka (ex: `encargos.saldo.atualizar`)
- Serialização do payload com número da conta, tipo e valor

**Contrato do evento de atualização:**
```json
{
  "numeroConta": "12345-6",
  "tipoLancamento": "DEBITO",
  "valor": 150.00,
  "idLancamento": "uuid"
}
```

**Impacto:** Sem isso, o saldo das contas nunca é atualizado no Sistema de Contas.

---

### 4.3 ❌ REST API de Consulta de Resultados

**Situação:** A interface `ConsultarResultadoProcessamentoPort` existe mas não tem nenhuma implementação. Não há controller de consulta.

**O que precisa ser implementado:**
1. `ResultadoProcessamentoRepository` (Spring Data JPA ou JDBC)
2. Implementação de `ConsultarResultadoProcessamentoPort`
3. Controller REST com endpoints:
   - `GET /api/v1/lancamentos?numeroConta={conta}&dataInicio={data}&dataFim={data}&status={status}&page={n}&size={n}`
   - `GET /api/v1/lancamentos/{idLancamento}`

**Impacto:** A API de Consulta ao Cliente downstream não tem como consultar os resultados.

---

### 4.4 ❌ `LancamentoPartitioner` não conectado ao Job

**Situação:** `LancamentoPartitioner` está implementado mas o `BatchJobConfig` usa step single-thread multi-chunk, não step particionado.

**Opção A — Manter abordagem atual (stream S3 multi-thread):**
- `SynchronizedItemStreamReader` + `SimpleAsyncTaskExecutor` com Virtual Threads
- O `LancamentoPartitioner` se torna código morto → remover

**Opção B — Migrar para step particionado:**
- Requer download do arquivo para disco antes do particionamento (para calcular `startLine`)
- Conflita com a abordagem de stream direto do S3 adotada
- Não recomendada dado o design atual

**Recomendação:** Remover `LancamentoPartitioner` ou documentar como código reservado.

---

### 4.5 ❌ Configuração dos Tópicos Kafka

Os tópicos Kafka não estão declarados explicitamente no código (apenas configuração genérica no `application.yaml`). Falta:
- Declaração dos `NewTopic` beans para criação automática
- Configuração de partições e replication factor
- Definição dos nomes dos tópicos como constantes ou properties

---

## 5. Inconsistências e Código Morto

### 5.1 ⚠️ Dois métodos de acesso S3 coexistindo

`S3FileDownloadAdapter` possui dois métodos:
- `abrirStreamArquivoDoDia()` — **atual**, usado pelo batch (stream direto)
- `downloadArquivoDoDia()` / `downloadArquivo(LocalDate)` — **legado**, baixa para disco

O método `downloadArquivoDoDia()` referencia `localTempDir` que foi removido do `application.yaml` (comentário no yaml: `# local-temp-dir removido`). Se chamado em produção, fará NPE ao tentar `Path.of(null, nomeArquivo)`.

**Ação:** Remover os métodos de download para disco (`downloadArquivoDoDia`, `downloadArquivo`) ou marcar como deprecated com `@Deprecated`.

---

### 5.2 ⚠️ `StubPersistirResultadoAdapter` não é utilizado

A porta `PersistirResultadoPort` existe e tem uma implementação stub, mas o batch persiste diretamente via `JdbcBatchItemWriter` em `BatchJobConfig` — não passa pela porta. O stub está registrado como `@Component` e injetado pelo Spring sem uso.

**Opções:**
- Remover `PersistirResultadoPort` e o stub (a persistência é responsabilidade do Spring Batch writer)
- Ou inverter: fazer o `LancamentoProcessor` retornar apenas o resultado e delegar a persistência para um writer que usa a porta

---

### 5.3 ⚠️ `S3DownloadTasklet` — classe existe mas não está no job

Existe `S3DownloadTasklet.java` e seu teste `S3DownloadTaskletTest.java`, mas o `BatchJobConfig` não usa nenhum tasklet — usa stream direto. A classe é código não conectado.

---

### 5.4 ⚠️ `Exemplo.java` no domínio

Existe `br.com.banco.processamento_encargos.domain.model.Exemplo.java` que parece ser um arquivo de scaffold. Deve ser removido.

---

### 5.5 ⚠️ `application-default.yaml` vazio ou mínimo

Existe `application-default.yaml` mas sem conteúdo relevante identificado. Verificar se é necessário.

---

## 6. Plano de Ação — Próximas Entregas

### Prioridade 1 — Adapters Kafka (bloqueador de produção)

**Tarefa 1.1 — `KafkaConsultarClienteContaAdapter`**

```
adapter/out/kafka/
├── KafkaConsultarClienteContaAdapter.java   # implementa ConsultarClienteContaPort
├── KafkaTopicsConfig.java                   # @Bean NewTopic para cada tópico
└── dto/
    ├── ConsultaContaRequestEvent.java
    └── ConsultaContaResponseEvent.java
```

- Usar `ReplyingKafkaTemplate<String, String, String>` com timeout configurável
- Correlacionar por `idLancamento` (correlationId)
- Tratar timeout → retornar `ContaInfo` com `StatusConta.INDISPONIVEL`
- Tópicos: `encargos.conta.consulta.request` / `encargos.conta.consulta.response`

**Tarefa 1.2 — `KafkaAtualizarSaldoContaAdapter`**

```
adapter/out/kafka/
└── KafkaAtualizarSaldoContaAdapter.java     # implementa AtualizarSaldoContaPort
```

- Usar `KafkaTemplate<String, String>`
- Serializar payload como JSON
- Chave da mensagem: `numeroConta` (garante ordenação por conta na partição Kafka)
- Tópico: `encargos.saldo.atualizar`

---

### Prioridade 2 — API REST de Consulta

**Tarefa 2.1 — Repository e implementação da porta**

```
adapter/out/persistence/
├── ResultadoProcessamentoEntity.java        # entidade JPA
├── ResultadoProcessamentoJpaRepository.java # Spring Data JPA
└── ConsultarResultadoProcessamentoAdapter.java  # implementa ConsultarResultadoProcessamentoPort
```

**Tarefa 2.2 — Controller REST**

```
adapter/in/rest/
├── LancamentoController.java                # GET /api/v1/lancamentos
└── dto/response/
    ├── LancamentoResponse.java
    └── PagedLancamentoResponse.java
```

Endpoints:
```
GET /api/v1/lancamentos
  ?numeroConta=&dataInicio=&dataFim=&status=&page=0&size=20

GET /api/v1/lancamentos/{idLancamento}
```

---

### Prioridade 3 — Limpeza de código

| Item | Ação |
|------|------|
| `Exemplo.java` | Remover |
| `S3DownloadTasklet.java` | Remover ou conectar ao job |
| `downloadArquivoDoDia()` / `downloadArquivo()` | Remover de `S3FileDownloadAdapter` |
| `LancamentoPartitioner` | Remover ou documentar como reserva |
| `StubPersistirResultadoAdapter` | Remover se `PersistirResultadoPort` não for usada pelo batch |
| Stubs (`StubConsultarClienteContaAdapter`, `StubAtualizarSaldoContaAdapter`) | Manter apenas para profile `local` / testes |

---

### Prioridade 4 — Testes de Integração

Dependência `testcontainers-bom` já está no `pom.xml`. Implementar:

```
test/java/.../integration/
├── BatchIntegrationTest.java     # Testcontainers: PostgreSQL real
├── KafkaIntegrationTest.java     # EmbeddedKafka ou Testcontainers Kafka
└── S3IntegrationTest.java        # LocalStack
```

---

## 7. Riscos Identificados

| Risco | Severidade | Mitigação |
|-------|-----------|-----------|
| Stubs em produção (Kafka) | **CRÍTICO** | Prioridade 1: implementar adapters Kafka reais antes do deploy |
| `downloadArquivoDoDia()` com `localTempDir=null` causando NPE | **ALTO** | Remover método legado |
| `LancamentoPartitioner` conta linhas via `Files.lines()` em arquivo 20M — consumo de memória | **MÉDIO** | Remover ou substituir por contagem por bytes |
| Sem API de consulta: downstream sem fonte de dados | **ALTO** | Prioridade 2: implementar REST de consulta |
| `ReplyingKafkaTemplate` timeout em pico de 20M registros | **MÉDIO** | Configurar timeout adequado; tratar `INDISPONIVEL` graciosamente |
| Ausência de testes de integração com banco real | **MÉDIO** | Implementar com Testcontainers |
| `application-default.yaml` sobrescrevendo configs sem controle | **BAIXO** | Revisar conteúdo do arquivo |

---

## Status Resumido

```
DOMÍNIO              ████████████████████  100% ✅
BATCH / CSV          █████████████████░░░   85% ⚠️  (Partitioner desconectado)
PERSISTÊNCIA DB      ████████████████████  100% ✅
ADAPTER S3           ████████████████░░░░   80% ⚠️  (Método legado de download)
ADAPTER KAFKA        ████░░░░░░░░░░░░░░░░   20% ❌  (Apenas stubs)
REST API             ██████░░░░░░░░░░░░░░   30% ❌  (Só trigger do job, sem consulta)
TESTES UNITÁRIOS     ████████████████░░░░   80% ✅
TESTES INTEGRAÇÃO    ░░░░░░░░░░░░░░░░░░░░    0% ❌
INFRA / DOCKER       ████████████████████  100% ✅
OBSERVABILIDADE      ████████████████░░░░   80% ⚠️  (Métricas customizadas do batch ausentes)
```