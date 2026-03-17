# processamento-encargos

![Versão](https://img.shields.io/badge/versão-0.0.1--SNAPSHOT-blue)
![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.11-brightgreen)
![Build](https://img.shields.io/badge/build-passing-brightgreen)
![Testes](https://img.shields.io/badge/testes-97%20passing-brightgreen)

Microsserviço responsável pelo **processamento diário de encargos em contas correntes**. Lê um arquivo CSV de lançamentos armazenado no Amazon S3, valida e processa cada lançamento consultando o sistema de contas via Kafka, persiste os resultados no banco de dados e publica eventos contábeis para os serviços downstream.

---

## Índice

- [Visão Geral](#visão-geral)
- [Tecnologias](#tecnologias)
- [Arquitetura](#arquitetura)
- [Pré-requisitos](#pré-requisitos)
- [Instalação](#instalação)
- [Configuração](#configuração)
- [Uso](#uso)
- [API](#api)
- [Fluxo de Eventos Kafka](#fluxo-de-eventos-kafka)
- [Testes](#testes)
- [Estrutura do Projeto](#estrutura-do-projeto)
- [Roadmap](#roadmap)

---

## Visão Geral

O `processamento-encargos` é acionado diariamente (via scheduler interno ou chamada HTTP) para processar o arquivo de lançamentos do dia. Para cada lançamento no CSV:

1. Consulta os dados e saldo da conta no sistema externo via Kafka request/response
2. Valida as regras de negócio (conta ativa, sem bloqueio judicial, saldo suficiente para débito etc.)
3. Persiste o resultado (`PROCESSADO` ou `REJEITADO`) no banco de dados
4. Publica um evento contábil no Kafka para o serviço de gestão contábil
5. Aguarda a confirmação contábil e, ao recebê-la, aciona a atualização do saldo da conta

**Principais funcionalidades:**

- Processamento paralelo multi-threaded com Spring Batch e Java 21 Virtual Threads
- Leitura direta do arquivo CSV via stream S3 (sem download para disco)
- Outbox Pattern: eventos Kafka são persistidos no banco antes de publicar, garantindo entrega mesmo em caso de falha do broker
- Circuit Breaker (Resilience4j) para o sistema de contas: evita degradação em cascata em caso de indisponibilidade
- Dead Letter Queue: mensagens com falha são encaminhadas para tópicos `.DLT` após 3 tentativas, sem perda silenciosa
- Métricas expostas via Micrometer/Prometheus para observabilidade em produção

---

## Tecnologias

| Categoria        | Tecnologia                    | Versão     |
|------------------|-------------------------------|------------|
| Linguagem        | Java                          | 21 (LTS)   |
| Framework        | Spring Boot                   | 3.5.11     |
| Batch            | Spring Batch                  | 5.x        |
| Mensageria       | Apache Kafka (Spring Kafka)   | —          |
| Banco de Dados   | PostgreSQL (AWS RDS)          | —          |
| Migrations       | Flyway                        | 11.x       |
| Armazenamento    | Amazon S3 (AWS SDK v2)        | 2.25.14    |
| Resiliência      | Resilience4j                  | 2.2.0      |
| Métricas         | Micrometer + Prometheus       | —          |
| Build            | Maven Wrapper                 | 3.9.x      |
| Testes           | JUnit 5 + Mockito             | —          |

---

## Arquitetura

O projeto segue **Arquitetura Hexagonal (Ports and Adapters)**. O domínio central não possui dependências externas — sem anotações Spring ou Lombok nos UseCases e modelos de domínio. Toda comunicação com sistemas externos passa por interfaces de porta.

```
                    ┌──────────────────────────────────────────────────┐
                    │                   CORE (Domínio)                  │
                    │                                                   │
  ┌─────────────┐   │  ProcessarLancamentoUseCase                       │
  │ POST /batch │──▶│  ValidacaoLancamentoUseCase                       │
  │  /executar  │   │                                                   │
  └─────────────┘   │  Lancamento · ResultadoProcessamento · ContaInfo  │
                    │  StatusProcessamento · TipoLancamento             │
  ┌─────────────┐   │                                                   │
  │  Scheduler  │──▶│                  Output Ports                     │
  │  (cron)     │   │  ConsultarClienteConta  · PublicarLancamentoContab│
  └─────────────┘   │  AtualizarSaldoConta    · SalvarResultadoProc.    │
                    │  CarregarArquivoLancamentos                       │
  ┌─────────────┐   └──────────────────────┬────────────────────────────┘
  │   Kafka     │                          │ implementado por
  │  Consumer   │◀──────────────    ┌──────▼──────────────────────────────┐
  └─────────────┘                   │        ADAPTERS (Infraestrutura)     │
                                    │                                      │
                                    │  output/producer  → Kafka            │
                                    │  output/repository→ PostgreSQL (JPA) │
                                    │  output/client    → Amazon S3        │
                                    │  input/batch      → Spring Batch     │
                                    │  input/consumer   → Kafka consumer   │
                                    │  input/controller → REST API         │
                                    └──────────────────────────────────────┘
```

### Fluxo principal de processamento

```
S3 (lancamentos.csv)
        │
        ▼
   FlatFileItemReader ── SynchronizedItemStreamReader (thread-safe)
        │
        ▼  chunks em paralelo (Virtual Threads)
   LancamentoProcessor
        │
        ├─① Kafka request → Sistema de Contas
        │       └─② Kafka response ← (ContaInfo: saldo, status)
        │
        ├─③ ValidacaoLancamentoUseCase
        │       └─ REJEITADO → persiste resultado e encerra
        │
        ├─④ Persiste saldo pendente (tabela saldo_pendente)
        │
        └─⑤ Publica LancamentoContabilEvent → Kafka
                  │
                  ▼
         gestao-contabil (serviço externo)
                  │
                  └─⑥ Kafka response ← ConfirmacaoContabilEvent
                            │
                            └─⑦ Publica AtualizarSaldoEvent → Kafka
                                        │
                                        ▼
                               sistema-contas (atualiza saldo)
```

---

## Pré-requisitos

- Java 21+
- Maven 3.9+ (ou usar o `./mvnw` incluso)
- PostgreSQL acessível (RDS ou local)
- Apache Kafka acessível
- Credenciais AWS com acesso ao bucket S3

---


## Configuração

Configure as variáveis de ambiente antes de iniciar a aplicação:

| Variável                          | Descrição                                         | Obrigatória | Padrão                                   |
|-----------------------------------|---------------------------------------------------|-------------|------------------------------------------|
| `DB_USER`                         | Usuário do banco PostgreSQL                       | Sim         | —                                        |
| `DB_PASSWORD`                     | Senha do banco PostgreSQL                         | Sim         | —                                        |
| `KAFKA_BOOTSTRAP_SERVERS`         | Endereço(s) do broker Kafka                       | Não         | `localhost:9092`                         |
| `AWS_S3_ACCESS_KEY`               | Access key AWS para acesso ao S3                  | Sim         | —                                        |
| `AWS_S3_SECRET_KEY`               | Secret key AWS para acesso ao S3                  | Sim         | —                                        |
| `AWS_S3_REGION`                   | Região AWS do bucket                              | Sim         | —                                        |
| `AWS_S3_BUCKET_NAME`              | Nome do bucket S3 com o arquivo CSV               | Sim         | —                                        |
| `BATCH_CHUNK_SIZE`                | Número de itens por chunk do batch                | Não         | `1000`                                   |
| `BATCH_PARTITIONS`                | Número de threads paralelas no batch              | Não         | `10`                                     |
| `S3_KEY_PREFIX`                   | Prefixo do path do arquivo no S3                  | Não         | _(vazio)_                                |
| `KAFKA_TOPIC_CONSULTA_REQUEST`    | Tópico de request de consulta de conta            | Não         | `encargos.conta.consulta.request`        |
| `KAFKA_TOPIC_CONSULTA_RESPONSE`   | Tópico de response de consulta de conta           | Não         | `encargos.conta.consulta.response`       |
| `KAFKA_TOPIC_LANCAMENTO_CONTABIL` | Tópico de publicação de lançamento contábil       | Não         | `encargos.contabil.lancamento.request`   |
| `KAFKA_TOPIC_CONFIRMACAO_CONTABIL`| Tópico de confirmação contábil                    | Não         | `encargos.contabil.lancamento.response`  |
| `KAFKA_TOPIC_ATUALIZAR_SALDO`     | Tópico de atualização de saldo                    | Não         | `encargos.conta.atualizar-saldo`         |
| `KAFKA_CONSULTA_TIMEOUT_SECONDS`  | Timeout (s) para resposta de consulta de conta    | Não         | `5`                                      |
| `OUTBOX_RETRY_INTERVAL_MS`        | Intervalo (ms) entre tentativas do outbox         | Não         | `30000`                                  |
| `OUTBOX_MAX_TENTATIVAS`           | Máximo de tentativas antes de descartar do outbox | Não         | `5`                                      |

---

## Uso

```bash
# Iniciar a aplicação
./mvnw spring-boot:run

# Ou via JAR compilado
java -jar target/processamento-encargos-0.0.1-SNAPSHOT.jar
```

A aplicação sobe na porta `8083`. O job de encargos **não executa automaticamente no startup** (`spring.batch.job.enabled: false`). Ele pode ser disparado via scheduler interno (cron) ou manualmente via API.

---

## API

Base URL: `http://localhost:8083`

| Método | Endpoint                   | Descrição                                               |
|--------|----------------------------|---------------------------------------------------------|
| `POST` | `/api/v1/batch/executar`   | Dispara manualmente o job de processamento de encargos  |
| `GET`  | `/actuator/health`         | Status de saúde da aplicação                            |
| `GET`  | `/actuator/metrics`        | Métricas da aplicação (Micrometer)                      |
| `GET`  | `/actuator/prometheus`     | Métricas no formato Prometheus                          |

**Exemplo — disparar o job:**

```bash
curl -X POST http://localhost:8083/api/v1/batch/executar
# Resposta: Job disparado. Status: COMPLETED
```

---

## Fluxo de Eventos Kafka

| Tópico                                       | Direção | Descrição                                              |
|----------------------------------------------|---------|--------------------------------------------------------|
| `encargos.conta.consulta.request`            | Produz  | Request de consulta de dados da conta                  |
| `encargos.conta.consulta.response`           | Consome | Response com saldo e status da conta                   |
| `encargos.contabil.lancamento.request`       | Produz  | Lançamento contábil para o serviço de gestão contábil  |
| `encargos.contabil.lancamento.response`      | Consome | Confirmação do processamento contábil                  |
| `encargos.conta.atualizar-saldo`             | Produz  | Comando de atualização de saldo para o sistema-contas  |
| `encargos.contabil.lancamento.response.DLT`  | DLT     | Mensagens de confirmação contábil que falharam         |
| `encargos.conta.consulta.response.DLT`       | DLT     | Mensagens de resposta de consulta que falharam         |

### Resiliência de eventos

- **Outbox Pattern**: ao publicar lançamento contábil, o payload é salvo na tabela `lancamento_contabil_pendente` antes do envio ao Kafka. Um scheduler reprocessa os pendentes a cada 30s (configurável), descartando após `OUTBOX_MAX_TENTATIVAS` falhas.
- **Saldo pendente**: a atualização de saldo é registrada na tabela `saldo_pendente` e publicada somente após a confirmação contábil chegar, eliminando o risco de race condition e perda em restart.
- **Circuit Breaker**: a consulta ao sistema de contas é protegida por Resilience4j. Após 50% de falhas em uma janela de 10 chamadas, o circuito abre por 30s e retorna `INDISPONIVEL` imediatamente, sem aguardar o timeout de 5s.
- **Dead Letter Queue**: mensagens que falham após 3 tentativas (intervalo de 2s) são encaminhadas para o tópico `.DLT`, onde podem ser inspecionadas e reprocessadas manualmente.

---

## Testes

```bash
# Executar todos os testes unitários
JAVA_HOME="C:/Program Files/Java/jdk-21" ./mvnw test

# Executar com relatório de cobertura (threshold mínimo: 80%)
JAVA_HOME="C:/Program Files/Java/jdk-21" ./mvnw verify
```

O projeto possui **97 testes unitários** com cobertura acima de 80%:

| Camada                                           | Testes |
|--------------------------------------------------|--------|
| `core/usecase` (ProcessarLancamento, Validacao)  | 15     |
| `core/domain/model`                              | 10     |
| `adapter/input/batch`                            | 18     |
| `adapter/input/consumer`                         | 4      |
| `adapter/output/producer`                        | 18     |
| `adapter/output/repository`                      | 5      |
| `adapter/output/client` (S3)                     | 9      |
| `config` (KafkaTopics, KafkaConsumer, Domain)    | 13     |
| `adapter/input/controller` + `rest`              | 3      |

---

## Estrutura do Projeto

```
processamento-encargos/
├── src/main/java/.../
│   ├── core/
│   │   ├── domain/model/           # Modelos de domínio puros (sem anotações externas)
│   │   │   ├── Lancamento.java
│   │   │   ├── ResultadoProcessamento.java
│   │   │   ├── ContaInfo.java
│   │   │   ├── TipoLancamento.java
│   │   │   └── StatusProcessamento.java
│   │   └── usecase/                # Regras de negócio
│   │       ├── ProcessarLancamentoUseCase.java
│   │       └── ValidacaoLancamentoUseCase.java
│   │
│   ├── port/
│   │   ├── input/                  # Contratos de entrada no domínio
│   │   └── output/                 # Contratos de saída do domínio para infraestrutura
│   │
│   ├── adapter/
│   │   ├── input/
│   │   │   ├── batch/              # Spring Batch: Job, Step, Reader, Processor, Scheduler
│   │   │   ├── consumer/           # Kafka: consumidor de confirmação contábil
│   │   │   ├── controller/         # REST: disparo manual do job
│   │   │   └── rest/               # GlobalExceptionHandler
│   │   └── output/
│   │       ├── producer/           # Kafka: consulta, contábil, saldo, scheduler de outbox
│   │       ├── repository/         # JPA: resultado, saldo_pendente, lc_pendente
│   │       │   └── entity/         # Entidades JPA
│   │       └── client/             # AWS S3: download via stream
│   │
│   └── config/                     # Beans de configuração (Domain, Kafka, Resilience4j)
│
├── src/main/resources/
│   ├── application.yaml            # Configuração da aplicação
│   └── db/migration/               # Migrations Flyway
│       ├── V1__create_resultado_processamento.sql
│       └── V2__create_outbox_tables.sql
│
├── src/test/                       # Testes unitários (espelha estrutura de main)
├── pom.xml
└── README.md
```

---

## Roadmap

- [x] Processamento batch multi-threaded com Spring Batch e Virtual Threads
- [x] Leitura de CSV via stream direto do S3 (sem download para disco)
- [x] Arquitetura Hexagonal (Ports and Adapters) com domínio livre de dependências externas
- [x] Outbox Pattern para garantia de entrega dos eventos Kafka
- [x] Circuit Breaker (Resilience4j) para o sistema de contas
- [x] Dead Letter Queue para consumers Kafka
- [x] Métricas Micrometer (PROCESSADO, REJEITADO, skips, outbox, publicações contábeis)
- [ ] Testes de integração com Testcontainers (PostgreSQL + Kafka)
- [ ] Pre-fetch assíncrono de contas para eliminar bloqueio sequencial por item no batch