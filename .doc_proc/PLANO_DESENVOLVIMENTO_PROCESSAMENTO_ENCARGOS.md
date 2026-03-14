# Plano de Desenvolvimento — Processamento de Encargos em Conta Corrente

> **Documento base:** `case_tecnico_processamento_encargos.docx`
> **Autor do plano:** Engenheiro de Software Sênior
> **Data:** 2026-03-10

---

## Sumário

1. [Visão Geral do Problema](#1-visão-geral-do-problema)
2. [Decisões Arquiteturais](#2-decisões-arquiteturais)
3. [Estrutura do Projeto](#3-estrutura-do-projeto)
4. [Fases de Desenvolvimento](#4-fases-de-desenvolvimento)
   - [Fase 1 — Setup e Fundação](#fase-1--setup-e-fundação)
   - [Fase 2 — Domínio e Regras de Negócio](#fase-2--domínio-e-regras-de-negócio)
   - [Fase 3 — Ingestão do Arquivo CSV](#fase-3--ingestão-do-arquivo-csv)
   - [Fase 4 — Comunicação por Eventos (Kafka)](#fase-4--comunicação-por-eventos-kafka)
   - [Fase 5 — Persistência dos Resultados](#fase-5--persistência-dos-resultados)
   - [Fase 6 — API REST de Consulta](#fase-6--api-rest-de-consulta)
   - [Fase 7 — Resiliência e Escalabilidade](#fase-7--resiliência-e-escalabilidade)
   - [Fase 8 — Testes](#fase-8--testes)
   - [Fase 9 — Observabilidade e Deploy](#fase-9--observabilidade-e-deploy)
5. [Contrato de Dados](#5-contrato-de-dados)
6. [Regras de Negócio Críticas](#6-regras-de-negócio-críticas)
7. [Cronograma Estimado](#7-cronograma-estimado)
8. [Riscos e Mitigações](#8-riscos-e-mitigações)

---

## 1. Visão Geral do Problema

O sistema deve **processar diariamente um arquivo CSV** contendo até **20 milhões de lançamentos** (débitos e créditos) a serem aplicados em contas correntes. O processamento deve:

- Iniciar às **04:00** e concluir até **06:00** (janela de 2 horas).
- Consultar o status de cada conta via **comunicação por eventos** com o Sistema de Contas externo.
- **Rejeitar débitos** em contas com Bloqueio Judicial ou status diferente de ATIVA.
- **Atualizar o saldo** da conta quando o processamento for bem-sucedido.
- Persistir o resultado de cada lançamento e disponibilizá-lo via **API REST**.

### Ecossistema de sistemas (do diagrama arquitetural)

```
┌─────────────────────────┐     eventos Kafka      ┌─────────────────────────┐
│                         │ ─────────────────────► │                         │
│  PROCESSADOR ENCARGOS   │                         │   SISTEMA DE CONTAS     │
│  (este sistema — batch) │ ◄───────────────────── │   (sistema externo)     │
│                         │     eventos Kafka       │                         │
│  ┌─────────────────┐    │                         └─────────────────────────┘
│  │  PostgreSQL     │    │
│  │  (resultados)   │    │
│  └────────┬────────┘    │
│           │ expõe       │
│      API REST           │
└───────────┼─────────────┘
            │ consome
            ▼
┌─────────────────────────┐
│  API CONSULTA CLIENTE   │
│  (sistema downstream)   │
│  Atende clientes finais │
└─────────────────────────┘
```

> **Escopo deste plano:** Implementar o **Processador de Encargos** completo, incluindo a API REST que serve como fonte para a API de Consulta ao Cliente. O Sistema de Contas e a API de Consulta ao Cliente são sistemas externos — o plano cobre apenas os **contratos de integração** com eles.

---

## 2. Decisões Arquiteturais

### 2.1 Arquitetura Hexagonal (Ports & Adapters)

**Por quê:** O case exige separação clara entre regras de negócio e integrações externas (arquivo CSV, Kafka, banco de dados, API). A Arquitetura Hexagonal garante que o núcleo de domínio seja **independente de frameworks e infraestrutura**, facilitando testes unitários e troca de adaptadores sem impacto no negócio.

```
[ CSV Adapter ] ──► [ Port: ProcessarLancamento ] ──► [ Domain Core ]
[ Kafka Adapter ] ──► [ Port: ConsultarStatusConta ] ──► [ Domain Core ]
[ JPA Adapter ] ◄── [ Port: PersistirResultado ] ◄── [ Domain Core ]
[ REST Adapter ] ◄── [ Port: ConsultarLancamento ] ◄── [ Domain Core ]
```

### 2.2 Java 21 + Spring Boot 3.x

**Por quê:** Requisito técnico explícito do case. Spring Boot 3 traz suporte nativo a Virtual Threads (Project Loom, Java 21), que são essenciais para processar 20M de registros com alta concorrência e baixo consumo de memória.

### 2.3 Apache Kafka para Comunicação por Eventos

**Por quê:** O case determina "comunicação com sistema de contas via eventos". Kafka oferece:
- **Alta throughput** compatível com o volume de 20M registros em 2 horas (~2.778 msg/s).
- **Desacoplamento** entre o serviço de encargos e o sistema de contas.
- **Replayability** para reprocessamento em caso de falha.
- Garantia de **entrega ordenada por partição** (por número de conta).

### 2.4 Processamento em Chunks com Spring Batch

**Por quê:** 20 milhões de registros em arquivo flat é um caso clássico de batch. Spring Batch fornece:
- Leitura por chunks com controle de cursor (baixo consumo de memória).
- Checkpoint e restart em caso de falha (não precisa reprocessar tudo).
- Step partitioning para paralelismo.
- Integração nativa com Spring Boot.

### 2.5 PostgreSQL para Persistência

**Por quê:** Volume previsível e estruturado (lançamentos com schema fixo), necessidade de consulta por API REST e suporte transacional. PostgreSQL tem excelente performance com bulk inserts e índices parciais.

### 2.6 Três Sistemas Distintos (conforme diagrama arquitetural)

A análise do diagrama revela **três sistemas com papéis distintos**, dispostos em dois blocos separados horizontalmente:

| Bloco | Sistema | Posição no diagrama | Responsabilidade |
|-------|---------|---------------------|-----------------|
| **Esquerdo (principal)** | Processador de Encargos (este sistema) | Esquerda — caixa azul grande com subcaixas internas | Lê CSV → valida → persiste resultado → expõe API |
| **Direito superior** | Sistema de Contas | Direita superior — caixa separada | Responde consultas de status/saldo via evento |
| **Direito inferior** | API de Consulta ao Cliente | Direita inferior — caixa separada | Expõe endpoint REST para consulta de lançamentos processados por clientes externos |

> **Insight chave do diagrama:** A **API de Consulta ao Cliente** é um sistema separado à direita — ela **consome** os dados persistidos pelo batch, não faz parte do fluxo de processamento. Isso significa que ela é um **leitor independente** do banco de resultados, com seu próprio ciclo de vida, possivelmente servindo clientes finais ou outros sistemas downstream.

```
┌──────────────────────────────────────────────────────────────────────────────────────────────┐
│                        ARQUITETURA ILUSTRATIVA (reconstruída do diagrama)                     │
│                                                                                              │
│  ┌─────────────────────────────────────┐          ┌──────────────────────────────────┐      │
│  │   PROCESSADOR DE ENCARGOS (batch)   │          │      SISTEMA DE CONTAS           │      │
│  │                                     │          │  (sistema externo)               │      │
│  │  ┌──────────────┐                   │  evento  │                                  │      │
│  │  │ Leitura CSV  │                   │─────────►│  Consulta status + saldo         │      │
│  │  │ (Spring Batch│                   │◄─────────│  Retorna: num_conta,             │      │
│  │  │  20M reg.)   │                   │  evento  │  nome_cliente, status, saldo     │      │
│  │  └──────┬───────┘                   │          └──────────────────────────────────┘      │
│  │         │                           │                                                    │
│  │         ▼                           │                                                    │
│  │  ┌──────────────┐                   │                                                    │
│  │  │  Validação   │                   │                                                    │
│  │  │  de Domínio  │                   │                                                    │
│  │  │  (regras de  │                   │                                                    │
│  │  │   negócio)   │                   │                                                    │
│  │  └──────┬───────┘                   │                                                    │
│  │         │                           │                                                    │
│  │    ┌────┴──────┐                    │                                                    │
│  │    ▼           ▼                    │                                                    │
│  │ [REJEITADO] [PROCESSADO]            │                                                    │
│  │    │           │ publica evento     │                                                    │
│  │    │           │ contábil           │                                                    │
│  │    └────┬──────┘                    │                                                    │
│  │         │                           │                                                    │
│  │         ▼                           │          ┌──────────────────────────────────┐      │
│  │  ┌──────────────┐                   │          │  API DE CONSULTA AO CLIENTE      │      │
│  │  │  PostgreSQL  │◄──────────────────┼──────────│  (sistema separado / downstream) │      │
│  │  │  (resultado_ │   lê resultados   │          │                                  │      │
│  │  │  processamento)                  │          │  Expõe REST para clientes        │      │
│  │  └──────────────┘                   │          │  externos consultarem seus       │      │
│  │                                     │          │  lançamentos processados         │      │
│  └─────────────────────────────────────┘          └──────────────────────────────────┘      │
│                                                                                              │
└──────────────────────────────────────────────────────────────────────────────────────────────┘
```

**Consequências arquiteturais desta leitura:**
- A **API de Consulta ao Cliente** NÃO precisa ser implementada neste sistema — ela é um consumidor externo do banco de dados ou de uma API REST que este batch expõe
- O batch precisa de dois canais Kafka: `request` (publica) e `response` (consome) para o Sistema de Contas
- Um terceiro canal Kafka opcional: `encargos.lancamento.processado` para sistemas contábeis downstream
- A `API de Consulta ao Cliente` pode ser servida por este mesmo serviço como um módulo REST separado, ou por um microserviço dedicado lendo o mesmo banco

---

## 3. Estrutura do Projeto

```
processamento-encargos/
├── src/
│   ├── main/
│   │   ├── java/br/com/banco/encargos/
│   │   │   ├── domain/                          # Núcleo do domínio (sem dependências externas)
│   │   │   │   ├── model/
│   │   │   │   │   ├── Lancamento.java           # Entidade de domínio
│   │   │   │   │   ├── ContaInfo.java            # VO com dados da conta (num, nome, status, saldo)
│   │   │   │   │   ├── ResultadoProcessamento.java
│   │   │   │   │   ├── StatusConta.java          # Enum: ATIVA, CANCELADA, BLOQUEIO_JUDICIAL
│   │   │   │   │   └── TipoLancamento.java       # Enum: DEBITO, CREDITO
│   │   │   │   ├── service/
│   │   │   │   │   ├── ValidacaoLancamentoService.java  # Regras de negócio puras
│   │   │   │   │   └── ProcessarEncargosService.java    # Orquestração do fluxo
│   │   │   │   └── port/
│   │   │   │       ├── in/                       # Portas de ENTRADA (driven by)
│   │   │   │       │   ├── ProcessarLancamentoUseCase.java
│   │   │   │       │   └── ConsultarLancamentoUseCase.java
│   │   │   │       └── out/                      # Portas de SAÍDA (driving)
│   │   │   │           ├── ConsultarClienteContaPort.java   # ← consome API externa de contas
│   │   │   │           ├── AtualizarSaldoContaPort.java     # ← notifica sistema de contas
│   │   │   │           └── PersistirResultadoPort.java      # ← persiste resultado
│   │   │   ├── application/                     # Casos de uso / orquestração
│   │   │   │   └── ProcessarEncargosUseCase.java
│   │   │   └── adapter/                         # Adaptadores de entrada e saída
│   │   │       ├── in/                          # ADAPTERS DE ENTRADA
│   │   │       │   ├── batch/                   # Adapter: leitura do CSV
│   │   │       │   │   ├── LancamentoCsvReader.java
│   │   │       │   │   ├── LancamentoProcessor.java
│   │   │       │   │   └── BatchJobConfig.java
│   │   │       │   └── rest/                    # Adapter: API REST de lançamentos (produzida)
│   │   │       │       ├── LancamentoController.java
│   │   │       │       └── dto/
│   │   │       │           └── ResultadoProcessamentoDTO.java
│   │   │       └── out/                         # ADAPTERS DE SAÍDA
│   │   │           ├── kafka/                   # Adapter: comunicação com sistema de contas
│   │   │           │   ├── ContaStatusEventProducer.java   # publica conta.status.request
│   │   │           │   ├── ContaStatusEventConsumer.java   # consome conta.status.response
│   │   │           │   └── event/
│   │   │           │       ├── ConsultaStatusContaEvent.java
│   │   │           │       └── RespostaStatusContaEvent.java
│   │   │           ├── persistence/             # Adapter: banco de dados
│   │   │           │   ├── ResultadoProcessamentoEntity.java
│   │   │           │   ├── LancamentoRepository.java
│   │   │           │   └── LancamentoJpaAdapter.java
│   │   │           └── conta/                   # Adapter: integração com API externa de contas
│   │   │               └── ContaKafkaAdapter.java  # implementa ConsultarClienteContaPort via Kafka
│   │   └── resources/
│   │       ├── application.yml
│   │       └── db/migration/
│   │           └── V1__create_resultado_processamento.sql
│   └── test/
│       └── java/br/com/banco/encargos/
│           ├── domain/
│           │   └── ValidacaoLancamentoServiceTest.java
│           ├── application/
│           │   └── ProcessarEncargosUseCaseTest.java
│           └── adapter/
│               ├── batch/
│               ├── rest/
│               └── kafka/
├── pom.xml
└── docker-compose.yml                           # Kafka + PostgreSQL locais
```

> **Nota sobre a separação de adapters:** O adapter `conta/ContaKafkaAdapter.java` é quem **consome a API do sistema de contas externo** (via eventos Kafka). O adapter `rest/LancamentoController.java` é a **API que este sistema produz** para consulta de lançamentos. São direções opostas e responsabilidades completamente distintas.

---

## 4. Fases de Desenvolvimento

---

### Fase 1 — Setup e Fundação

**Objetivo:** Criar a base do projeto com todas as dependências e configurações necessárias.

#### Passos:

**1.1 Inicializar o projeto Spring Boot**

```xml
<!-- pom.xml — dependências principais -->
<dependencies>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-batch</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
  </dependency>
  <dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
  </dependency>
  <dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
  </dependency>
</dependencies>
```

> **Por quê Flyway:** Controle versionado do schema de banco. Essencial em ambiente de batch onde a tabela de resultados deve existir antes do job rodar.

**1.2 Configurar `application.yml`**

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/encargos
  batch:
    jdbc:
      initialize-schema: always
    job:
      enabled: false           # Não executar na inicialização — controle via scheduler
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: encargos-processor
      auto-offset-reset: earliest

encargos:
  batch:
    chunk-size: 1000           # Processar 1.000 registros por transação
    partitions: 10             # 10 threads paralelas
    input-file: /data/lancamentos.csv
```

> **Por quê chunk-size 1000:** Equilíbrio entre memória consumida por chunk e overhead de commit. Com 20M registros e chunk 1000, haverá 20.000 transações — bem gerenciável.

**1.3 Subir ambiente local com Docker Compose**

```yaml
# docker-compose.yml
services:
  postgres:
    image: postgres:16
    environment:
      POSTGRES_DB: encargos
      POSTGRES_PASSWORD: encargos
    ports: ["5432:5432"]

  kafka:
    image: confluentinc/cp-kafka:7.6.0
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
    ports: ["9092:9092"]
    depends_on: [zookeeper]

  zookeeper:
    image: confluentinc/cp-zookeeper:7.6.0
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
```

---

### Fase 2 — Domínio e Regras de Negócio

**Objetivo:** Implementar o núcleo do domínio, completamente isolado de frameworks.

> **Por quê começar pelo domínio:** Domain-First Design garante que as regras de negócio críticas sejam implementadas e testadas sem dependência de infraestrutura. Reduz acoplamento e aumenta a cobertura de testes unitários.

**2.1 Modelar as entidades de domínio**

```java
// domain/model/Lancamento.java
public record Lancamento(
    String idLancamento,
    String numeroConta,
    TipoLancamento tipoLancamento,
    BigDecimal valor,
    LocalDate dataLancamento,
    String descricao
) {}

// domain/model/TipoLancamento.java
public enum TipoLancamento { DEBITO, CREDITO }

// domain/model/StatusConta.java
public enum StatusConta { ATIVA, CANCELADA, BLOQUEIO_JUDICIAL }

// domain/model/ResultadoProcessamento.java
public record ResultadoProcessamento(
    String idLancamento,
    String numeroConta,
    StatusProcessamento status,      // PROCESSADO, REJEITADO
    String motivoRejeicao,           // null se PROCESSADO
    LocalDateTime dataProcessamento
) {}
```

**2.2 Definir os Ports (interfaces)**

```java
// ── PORTAS DE SAÍDA (out-ports) ─────────────────────────────────────────────

// port/out/ConsultarClienteContaPort.java
// Implementada pelo adapter Kafka que consome o SISTEMA EXTERNO de contas
// NÃO é uma chamada direta HTTP — a comunicação é via eventos Kafka
public interface ConsultarClienteContaPort {
    /**
     * Publica evento de consulta e aguarda resposta do sistema de contas.
     * Retorna ContaInfo com: num_conta, nome_cliente, status, saldo
     */
    ContaInfo consultarCliente(String numeroConta, String correlationId);
}

// port/out/AtualizarSaldoContaPort.java
// Notifica o sistema de contas que o saldo deve ser atualizado (evento de sucesso)
public interface AtualizarSaldoContaPort {
    void publicarAtualizacaoSaldo(String numeroConta, TipoLancamento tipo, BigDecimal valor);
}

// port/out/PersistirResultadoPort.java
// Implementada pelo adapter JPA/JDBC — persiste no banco local
public interface PersistirResultadoPort {
    void persistir(ResultadoProcessamento resultado);
}
```

> **Por quê a `ConsultarClienteContaPort` está em `out/` e não em `in/`:** Do ponto de vista do domínio, consultar dados de uma conta é uma dependência **de saída** — o domínio precisa de dados externos para tomar sua decisão. O adapter que implementa essa port (via Kafka) é um detalhe de infraestrutura que o domínio desconhece completamente.

**2.3 Implementar as regras de negócio no serviço de domínio**

```java
// domain/service/ValidacaoLancamentoService.java
public class ValidacaoLancamentoService {

    public Optional<String> validar(Lancamento lancamento, ContaInfo conta) {
        // Regra 1: Conta com Bloqueio Judicial não aceita débito
        if (lancamento.tipoLancamento() == TipoLancamento.DEBITO
                && conta.status() == StatusConta.BLOQUEIO_JUDICIAL) {
            return Optional.of("CONTA_COM_BLOQUEIO_JUDICIAL");
        }

        // Regra 2: Conta cancelada não aceita nenhum lançamento
        if (conta.status() == StatusConta.CANCELADA) {
            return Optional.of("CONTA_CANCELADA");
        }

        return Optional.empty(); // válido
    }
}
```

> **Por quê Optional<String>:** Retorna o motivo da rejeição quando inválido, ou vazio quando válido — padrão expressivo que evita exceções para controle de fluxo.

---

### Fase 3 — Ingestão do Arquivo CSV

**Objetivo:** Implementar o adaptador de leitura do arquivo e o job Spring Batch.

#### Layout do arquivo CSV de entrada:

```
id_lancamento,numero_conta,tipo_lancamento,valor,data_lancamento,descricao
```

| Campo            | Tipo         | Exemplo                    |
|------------------|--------------|----------------------------|
| id_lancamento    | String (UUID)| `a1b2c3d4-...`             |
| numero_conta     | String       | `001234567-8`              |
| tipo_lancamento  | Enum         | `DEBITO` ou `CREDITO`      |
| valor            | Decimal      | `150.75`                   |
| data_lancamento  | Date         | `2026-03-10`               |
| descricao        | String       | `Encargos / Atraso / Estorno de tarifas` |

**3.1 Configurar o Job Spring Batch**

```java
// adapter/in/batch/BatchJobConfig.java
@Configuration
public class BatchJobConfig {

    @Bean
    public Job processarEncargosJob(JobRepository jobRepository, Step mainStep) {
        return new JobBuilder("processarEncargosJob", jobRepository)
                .start(mainStep)
                .build();
    }

    @Bean
    public Step mainStep(JobRepository jobRepository,
                         PlatformTransactionManager txManager,
                         FlatFileItemReader<Lancamento> reader,
                         LancamentoProcessor processor,
                         JdbcBatchItemWriter<ResultadoProcessamento> writer) {
        return new StepBuilder("mainStep", jobRepository)
                .<Lancamento, ResultadoProcessamento>chunk(1000, txManager)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .faultTolerant()
                .skipLimit(10_000)           // tolerar até 10K registros inválidos
                .skip(ValidationException.class)
                .build();
    }

    @Bean
    public FlatFileItemReader<Lancamento> csvReader(
            @Value("${encargos.batch.input-file}") String inputFile) {
        return new FlatFileItemReaderBuilder<Lancamento>()
                .name("lancamentoCsvReader")
                .resource(new FileSystemResource(inputFile))
                .linesToSkip(1)              // pular cabeçalho
                .delimited()
                .names("idLancamento","numeroConta","tipoLancamento",
                       "valor","dataLancamento","descricao")
                .fieldSetMapper(new BeanWrapperFieldSetMapper<>() {{
                    setTargetType(Lancamento.class);
                }})
                .build();
    }
}
```

> **Por quê faultTolerant com skip:** Em produção é inviável abortar um job de 20M registros por 1 linha malformada. O `skipLimit` define um teto de tolerância; os registros pulados são logados para auditoria.

**3.2 Particionamento para paralelismo**

```java
// Dividir o arquivo em N partições por intervalo de linhas
@Bean
public Step partitionedStep(StepSplitter splitter, Step workerStep) {
    return new StepBuilder("partitionedStep", jobRepository)
            .partitioner("workerStep", splitter)
            .step(workerStep)
            .gridSize(10)            // 10 threads = 2M registros por thread
            .taskExecutor(new SimpleAsyncTaskExecutor())
            .build();
}
```

> **Por quê 10 partições:** Com 20M registros e janela de 2h, precisamos de ~2.778 registros/segundo. Com 10 threads paralelas, cada thread processa ~278 registros/segundo — meta facilmente alcançável.

---

### Fase 4 — Comunicação por Eventos (Kafka)

**Objetivo:** Implementar o adapter que conecta o domínio ao sistema externo de contas via eventos Kafka. Este é o coração da integração descrita no diagrama: o batch **não chama diretamente** a API de consulta de cliente — ele se comunica por eventos e o sistema de contas responde de forma assíncrona.

#### Fluxo de eventos (conforme diagrama):

```
 PROCESSADOR DE ENCARGOS (este sistema)
 ┌──────────────────────────────────────────────────────────────────┐
 │                                                                  │
 │  [LancamentoProcessor]  ──chama──►  [ConsultarClienteContaPort] │
 │                                              │                   │
 │                                    implementada por              │
 │                                              │                   │
 │                                     [ContaKafkaAdapter]          │
 │                                              │                   │
 └──────────────────────────────────────────────┼───────────────────┘
                                                │ publica
                                                ▼
                                [Kafka: conta.status.request]
                                                │
                                                ▼
                         ┌──────────────────────────────────┐
                         │     SISTEMA DE CONTAS            │
                         │     (sistema externo — direita   │
                         │      superior no diagrama)        │
                         │                                  │
                         │  Recebe evento, consulta saldo,  │
                         │  publica resposta                │
                         └──────────────┬───────────────────┘
                                        │ publica
                                        ▼
                                [Kafka: conta.status.response]
                                        │
 ┌──────────────────────────────────────┼───────────────────┐
 │  PROCESSADOR (continua)              │                   │
 │                                      ▼                   │
 │                           [ContaKafkaAdapter]            │
 │                           correlaciona por               │
 │                           correlationId                  │
 │                                      │                   │
 │                                      ▼                   │
 │                           [Domínio — Validação]          │
 │                           aplica regras de negócio       │
 │                                      │                   │
 │                          ┌───────────┴────────────┐      │
 │                          ▼                        ▼      │
 │                     [REJEITADO]            [PROCESSADO]  │
 │                          │                    │          │
 │                          │              publica evento   │
 │                          │         [Kafka: encargos.     │
 │                          │          lancamento.processado│
 │                          │          → Sistema Contábil]  │
 │                          └──────────┬─────────┘          │
 │                                     │                    │
 │                                     ▼                    │
 │                              [PostgreSQL]                │
 │                         resultado_processamento           │
 └─────────────────────────────────────────────────────────┘

 ┌──────────────────────────────────────┐
 │  API DE CONSULTA AO CLIENTE          │   ← sistema separado
 │  (direita inferior no diagrama)      │     (downstream consumer)
 │                                      │
 │  Lê PostgreSQL ou consome API REST   │
 │  exposta pelo Processador            │
 │  → atende clientes finais            │
 └──────────────────────────────────────┘
```

> **Por quê o sistema de contas é externo e separado:** O diagrama arquitetural do case mostra claramente uma caixa "API Consulta Cliente" fora do batch. Ela tem seu próprio ciclo de vida, equipe e responsabilidade. O batch é apenas um **consumidor** dessa API — a integração via Kafka garante desacoplamento total.

**4.1 Adapter completo — ContaKafkaAdapter**

Este adapter implementa `ConsultarClienteContaPort` e encapsula toda a mecânica de publicação/consumo:

```java
// adapter/out/conta/ContaKafkaAdapter.java
@Component
public class ContaKafkaAdapter implements ConsultarClienteContaPort {

    private final KafkaTemplate<String, ConsultaStatusContaEvent> kafkaTemplate;
    private final ConcurrentHashMap<String, CompletableFuture<ContaInfo>> pendingRequests
            = new ConcurrentHashMap<>();

    // ── PUBLICAR CONSULTA ──────────────────────────────────────────────────
    @Override
    public ContaInfo consultarCliente(String numeroConta, String correlationId) {
        // 1. Registra o future ANTES de publicar (evita race condition)
        var future = new CompletableFuture<ContaInfo>();
        pendingRequests.put(correlationId, future);

        // 2. Publica evento de consulta para o sistema de contas
        var event = new ConsultaStatusContaEvent(correlationId, numeroConta);
        var record = new ProducerRecord<>("conta.status.request", numeroConta, event);
        record.headers().add("correlationId", correlationId.getBytes(StandardCharsets.UTF_8));
        kafkaTemplate.send(record);

        // 3. Aguarda resposta com timeout (janela de 2h não permite esperar indefinidamente)
        try {
            return future.orTimeout(5, TimeUnit.SECONDS)
                         .exceptionally(ex -> ContaInfo.indisponivel(numeroConta))
                         .get();
        } catch (Exception e) {
            pendingRequests.remove(correlationId);
            return ContaInfo.indisponivel(numeroConta);
        }
    }

    // ── CONSUMIR RESPOSTA DO SISTEMA DE CONTAS ─────────────────────────────
    @KafkaListener(topics = "conta.status.response", groupId = "encargos-processor")
    public void consumirResposta(ConsumerRecord<String, RespostaStatusContaEvent> record,
                                 Acknowledgment ack) {
        String correlationId = new String(
                record.headers().lastHeader("correlationId").value(),
                StandardCharsets.UTF_8);

        ContaInfo contaInfo = mapear(record.value());

        CompletableFuture<ContaInfo> future = pendingRequests.remove(correlationId);
        if (future != null) {
            future.complete(contaInfo);
        }
        ack.acknowledge(); // ACK manual: só confirma após processar
    }

    private ContaInfo mapear(RespostaStatusContaEvent event) {
        return new ContaInfo(
            event.numConta(),
            event.nomeCliente(),
            StatusConta.valueOf(event.status()),
            event.saldo()
        );
    }
}
```

**4.2 Eventos de contrato com o sistema externo**

```java
// adapter/out/kafka/event/ConsultaStatusContaEvent.java
// Publicado por este sistema → consome o sistema de contas
public record ConsultaStatusContaEvent(
    String correlationId,    // = idLancamento para rastreabilidade
    String numeroConta
) {}

// adapter/out/kafka/event/RespostaStatusContaEvent.java
// Publicado pelo sistema de contas → consumido por este sistema
public record RespostaStatusContaEvent(
    String numConta,
    String nomeCliente,
    String status,           // "ATIVA" | "CANCELADA" | "BLOQUEIO_JUDICIAL"
    BigDecimal saldo
) {}
```

> **Por quê `correlationId = idLancamento`:** Cada lançamento do CSV origina exatamente uma consulta ao sistema de contas. Usar o `idLancamento` como `correlationId` garante rastreabilidade fim-a-fim: é possível auditar qual evento de resposta correspondeu a qual linha do CSV.

**4.3 Timeout e fallback**

```java
// ContaInfo.indisponivel() — factory method para o caso de timeout
public static ContaInfo indisponivel(String numeroConta) {
    return new ContaInfo(numeroConta, null, StatusConta.INDISPONIVEL, BigDecimal.ZERO);
}
```

```java
// ValidacaoLancamentoService — regra adicional para indisponibilidade
if (conta.status() == StatusConta.INDISPONIVEL) {
    return Optional.of("SISTEMA_CONTAS_INDISPONIVEL");
}
```

**4.4 Dead Letter Queue**

```java
@Bean
public DefaultErrorHandler errorHandler(KafkaTemplate<?, ?> template) {
    var dlt = new DeadLetterPublishingRecoverer(template);
    var backOff = new ExponentialBackOff(1_000L, 2.0);
    backOff.setMaxElapsedTime(30_000L);  // máximo 30s de retry
    return new DefaultErrorHandler(dlt, backOff);
}
```

> **Por quê DLQ:** Respostas do sistema de contas que falham no desserialização ou processamento são desviadas para `conta.status.response.DLT` sem bloquear o fluxo principal. O lançamento correspondente é rejeitado com motivo `SISTEMA_CONTAS_INDISPONIVEL`.

### Fase 5 — Persistência dos Resultados

**Objetivo:** Persistir cada resultado de processamento no banco de dados para consulta futura.

**5.1 Migration Flyway — criar tabela**

```sql
-- db/migration/V1__create_resultado_processamento.sql
CREATE TABLE resultado_processamento (
    id                  BIGSERIAL PRIMARY KEY,
    id_lancamento       VARCHAR(36)    NOT NULL UNIQUE,
    numero_conta        VARCHAR(20)    NOT NULL,
    tipo_lancamento     VARCHAR(10)    NOT NULL,
    valor               NUMERIC(15,2)  NOT NULL,
    data_lancamento     DATE           NOT NULL,
    descricao           VARCHAR(255),
    status              VARCHAR(20)    NOT NULL,   -- PROCESSADO | REJEITADO
    motivo_rejeicao     VARCHAR(100),
    data_processamento  TIMESTAMP      NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_resultado_numero_conta ON resultado_processamento(numero_conta);
CREATE INDEX idx_resultado_status       ON resultado_processamento(status);
CREATE INDEX idx_resultado_data         ON resultado_processamento(data_lancamento);
```

> **Por quê índice em `numero_conta`:** A API REST precisará filtrar lançamentos por conta. Sem índice, uma query em 20M de linhas seria inviável. O índice em `data_lancamento` serve para filtros por período.

**5.2 JdbcBatchItemWriter para bulk insert**

```java
// adapter/out/persistence/ResultadoItemWriter.java
@Bean
public JdbcBatchItemWriter<ResultadoProcessamento> writer(DataSource dataSource) {
    return new JdbcBatchItemWriterBuilder<ResultadoProcessamento>()
            .dataSource(dataSource)
            .sql("""
                INSERT INTO resultado_processamento
                (id_lancamento, numero_conta, tipo_lancamento, valor,
                 data_lancamento, descricao, status, motivo_rejeicao)
                VALUES (:idLancamento, :numeroConta, :tipoLancamento, :valor,
                        :dataLancamento, :descricao, :status, :motivoRejeicao)
                ON CONFLICT (id_lancamento) DO NOTHING
            """)
            .beanMapped()
            .build();
}
```

> **Por quê `ON CONFLICT DO NOTHING`:** Em reprocessamentos (restart do job após falha), evita duplicidade de registros sem lançar exceção.

---

### Fase 6 — API REST de Consulta

**Objetivo:** Expor endpoints para consulta online dos lançamentos processados. Conforme o diagrama, esta API serve como **fonte de dados para a API de Consulta ao Cliente** (sistema downstream à direita no diagrama), além de uso interno.

> **Por quê este sistema expõe a API e não a API de Consulta ao Cliente diretamente:** O diagrama mostra que o banco de resultados (`resultado_processamento`) pertence ao Processador de Encargos. A API de Consulta ao Cliente consome esses dados — o padrão correto é que o dono dos dados exponha a API, e os consumidores a acessem. Isso evita que sistemas externos acessem o banco diretamente (violação de fronteira de domínio).

**6.1 Endpoints**

| Método | Path                                       | Consumidor esperado                     |
|--------|--------------------------------------------|-----------------------------------------|
| GET    | `/api/v1/lancamentos/{id}`                 | API Consulta Cliente / auditoria interna |
| GET    | `/api/v1/lancamentos?conta={num}&page={p}` | API Consulta Cliente (lista por conta)  |
| GET    | `/api/v1/lancamentos?status=REJEITADO`     | Operações / monitoramento interno       |
| GET    | `/api/v1/lancamentos/resumo?data={data}`   | Relatórios / dashboard operacional      |

**6.2 Implementação do Controller**

```java
// adapter/in/rest/LancamentoController.java
@RestController
@RequestMapping("/api/v1/lancamentos")
public class LancamentoController {

    private final ConsultarLancamentoUseCase consultarUseCase;

    @GetMapping("/{id}")
    public ResponseEntity<ResultadoProcessamentoDTO> buscarPorId(@PathVariable String id) {
        return consultarUseCase.buscarPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public Page<ResultadoProcessamentoDTO> listar(
            @RequestParam(required = false) String conta,
            @RequestParam(required = false) String status,
            Pageable pageable) {
        return consultarUseCase.listar(conta, status, pageable);
    }
}
```

> **Por quê paginação:** Contas com histórico extenso podem ter milhares de lançamentos. Retornar tudo de uma vez sobrecarregaria a memória do servidor e a rede. Paginação é obrigatória para APIs sobre grandes volumes.

---

### Fase 7 — Resiliência e Escalabilidade

**Objetivo:** Garantir que o sistema suporte o volume de 20M registros na janela de 2 horas e se recupere de falhas.

**7.1 Scheduler para disparo do job**

```java
// Disparo às 04:00 todos os dias
@Scheduled(cron = "0 0 4 * * *")
public void dispararJob() {
    jobLauncher.run(processarEncargosJob, new JobParametersBuilder()
            .addLocalDateTime("executedAt", LocalDateTime.now())
            .toJobParameters());
}
```

**7.2 Retry e Dead Letter Queue no Kafka**

```yaml
spring:
  kafka:
    consumer:
      properties:
        spring.json.trusted.packages: "*"
    listener:
      ack-mode: MANUAL
```

```java
// Configurar retry com backoff exponencial
@Bean
public DefaultErrorHandler errorHandler(KafkaTemplate<?, ?> template) {
    var dlt = new DeadLetterPublishingRecoverer(template);
    var backOff = new ExponentialBackOff(1000L, 2.0);
    backOff.setMaxElapsedTime(30_000L);  // máximo 30 segundos de retry
    return new DefaultErrorHandler(dlt, backOff);
}
```

> **Por quê DLQ:** Mensagens que falham repetidamente não bloqueiam o processamento das demais. Elas são desviadas para um tópico separado (`conta.status.response.DLT`) onde podem ser analisadas e reprocessadas manualmente.

**7.3 Connection Pool otimizado**

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
```

> **Por quê HikariCP com pool de 20:** Com 10 partições paralelas e cada uma fazendo commits em chunks de 1000, 20 conexões evitam contenção sem sobrecarregar o PostgreSQL.

---

### Fase 8 — Testes

**Objetivo:** Garantir qualidade do código com cobertura adequada em todas as camadas.

#### Estratégia de testes:

| Tipo                 | Ferramenta                  | Cobertura Alvo | O que testar                                     |
|----------------------|-----------------------------|----------------|--------------------------------------------------|
| Unitário             | JUnit 5 + Mockito           | ≥ 90%          | Regras de negócio, serviços, validações          |
| Integração           | Spring Boot Test + Testcontainers | ≥ 70%   | Leitura CSV, persistência, endpoints REST        |
| Integração Kafka     | EmbeddedKafkaBroker         | ≥ 70%          | Produção e consumo de eventos                    |
| Performance          | JMeter / k6                 | —              | Throughput do batch (≥ 2.778 registros/segundo)  |

**8.1 Teste unitário das regras de negócio**

```java
@Test
void deveRejeitarDebitoEmContaBloqueada() {
    var lancamento = new Lancamento("id1", "001", DEBITO, 
                                    new BigDecimal("100"), LocalDate.now(), "Encargo");
    var conta = new ContaInfo("001", "Cliente X", BLOQUEIO_JUDICIAL, new BigDecimal("500"));

    var resultado = validacaoService.validar(lancamento, conta);

    assertThat(resultado).isPresent();
    assertThat(resultado.get()).isEqualTo("CONTA_COM_BLOQUEIO_JUDICIAL");
}

@Test
void devePermitirCreditoEmContaBloqueada() {
    var lancamento = new Lancamento("id2", "001", CREDITO,
                                    new BigDecimal("100"), LocalDate.now(), "Estorno");
    var conta = new ContaInfo("001", "Cliente X", BLOQUEIO_JUDICIAL, new BigDecimal("500"));

    var resultado = validacaoService.validar(lancamento, conta);

    assertThat(resultado).isEmpty();  // crédito deve ser permitido mesmo com bloqueio
}
```

**8.2 Teste de integração com Testcontainers**

```java
@SpringBootTest
@Testcontainers
class BatchJobIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @Test
    void deveProcessarArquivoCsvComSucesso() throws Exception {
        // Arrange: copiar arquivo de teste com 1000 registros
        // Act: disparar job
        // Assert: verificar persistência dos resultados
    }
}
```

---

### Fase 9 — Observabilidade e Deploy

**Objetivo:** Garantir visibilidade operacional e facilitar o deploy em produção.

**9.1 Métricas com Micrometer + Prometheus**

```java
// Contadores e timers para o batch
@Autowired MeterRegistry registry;

Counter processados = Counter.builder("encargos.lancamentos.processados")
    .tag("status", "PROCESSADO").register(registry);
Counter rejeitados = Counter.builder("encargos.lancamentos.processados")
    .tag("status", "REJEITADO").register(registry);
```

**9.2 Logs estruturados (JSON) com Logback**

```xml
<!-- logback-spring.xml -->
<encoder class="net.logstash.logback.encoder.LogstashEncoder"/>
```

> **Por quê logs estruturados:** Em produção, os logs são ingeridos por plataformas como Elastic/Splunk. Formato JSON permite filtros e alertas automáticos sobre eventos críticos (ex.: taxa de rejeição acima de 5%).

**9.3 Health checks**

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health, metrics, prometheus, info
  health:
    kafka:
      enabled: true
```

**9.4 Containerização**

```dockerfile
# Dockerfile multi-stage
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app
COPY . .
RUN ./mvnw clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
ENTRYPOINT ["java", "-XX:+UseVirtualThreads", "-jar", "app.jar"]
```

> **Por quê `-XX:+UseVirtualThreads`:** Habilita o Project Loom do Java 21. Cada thread virtual consome ~1KB de memória vs ~1MB de thread de plataforma — crítico para 10+ threads paralelas com I/O intensivo.

---

## 5. Contrato de Dados

### 5.1 Arquivo CSV de Entrada

```
id_lancamento,numero_conta,tipo_lancamento,valor,data_lancamento,descricao
a1b2c3d4-e5f6-7890-abcd-ef1234567890,001234567-8,DEBITO,150.75,2026-03-10,Encargos
b2c3d4e5-f6a7-8901-bcde-f12345678901,001234567-8,CREDITO,50.00,2026-03-10,Estorno de tarifas
c3d4e5f6-a7b8-9012-cdef-123456789012,009876543-2,DEBITO,200.00,2026-03-10,Atraso
```

### 5.2 Evento de Requisição de Status (Kafka → Sistema de Contas)

**Tópico:** `conta.status.request`

```json
{
  "idLancamento": "a1b2c3d4-...",
  "numeroConta": "001234567-8"
}
```

### 5.3 Evento de Resposta de Status (Sistema de Contas → Kafka)

**Tópico:** `conta.status.response`

```json
{
  "num_conta": "001234567-8",
  "nome_cliente": "João da Silva",
  "status": "ATIVA",
  "saldo": 1500.00
}
```

### 5.4 Evento para Sistema Contábil (em caso de sucesso)

**Tópico:** `encargos.lancamento.processado`

```json
{
  "idLancamento": "a1b2c3d4-...",
  "numeroConta": "001234567-8",
  "tipoLancamento": "DEBITO",
  "valor": 150.75,
  "dataLancamento": "2026-03-10",
  "descricao": "Encargos",
  "novoSaldo": 1349.25
}
```

---

## 6. Regras de Negócio Críticas

| # | Regra | Comportamento |
|---|-------|---------------|
| R1 | Conta com `BLOQUEIO_JUDICIAL` **não aceita DÉBITO** | Rejeitar com motivo `CONTA_COM_BLOQUEIO_JUDICIAL` |
| R2 | Conta com `BLOQUEIO_JUDICIAL` **pode receber CRÉDITO** | Permitir processamento normalmente |
| R3 | Conta com status `CANCELADA` | Rejeitar qualquer lançamento com motivo `CONTA_CANCELADA` |
| R4 | Processamento com sucesso | Atualizar saldo da conta e publicar evento contábil |
| R5 | Timeout na consulta de status | Rejeitar com motivo `TIMEOUT_CONSULTA_CONTA` |
| R6 | Registro já processado (`id_lancamento` duplicado) | Ignorar silenciosamente (idempotência) |

---

## 7. Cronograma Estimado

| Fase | Atividade                             | Esforço estimado |
|------|---------------------------------------|------------------|
| 1    | Setup e fundação                      | 1 dia            |
| 2    | Domínio e regras de negócio           | 2 dias           |
| 3    | Ingestão CSV + Spring Batch           | 3 dias           |
| 4    | Comunicação por eventos (Kafka)       | 3 dias           |
| 5    | Persistência dos resultados           | 1 dia            |
| 6    | API REST de consulta                  | 2 dias           |
| 7    | Resiliência e escalabilidade          | 2 dias           |
| 8    | Testes (unitários + integração)       | 3 dias           |
| 9    | Observabilidade e deploy              | 2 dias           |
| —    | **Total**                             | **~19 dias**     |

---

## 8. Riscos e Mitigações

| Risco | Probabilidade | Impacto | Mitigação |
|-------|--------------|---------|-----------|
| Latência do Sistema de Contas maior que 5s | Média | Alto | Timeout por lançamento + motivo `SISTEMA_CONTAS_INDISPONIVEL` + DLQ |
| **Sistema de Contas (API Consulta Cliente) fora do ar** | Baixa | Crítico | Circuit Breaker (Resilience4j) + fallback para rejeição com log de auditoria |
| Arquivo CSV com linhas malformadas | Alta | Médio | `skipLimit` no Spring Batch + log de auditoria |
| Volume superior a 20M registros | Baixa | Alto | Configurar `partitions` dinamicamente via propriedade |
| Falha do job após 50% do processamento | Média | Alto | Spring Batch checkpoint: job reinicia do último chunk |
| Contenção no banco com 10 threads | Média | Alto | HikariCP + índices adequados + bulk insert via JDBC |
| Duplicidade de lançamentos no arquivo | Baixa | Médio | `ON CONFLICT DO NOTHING` na inserção + log de aviso |
| **Desalinhamento de contrato com sistema de contas** | Média | Alto | Versionar schemas Avro/JSON no Schema Registry do Kafka |

---

## Referências Técnicas

- [Spring Batch - Reference Documentation](https://docs.spring.io/spring-batch/docs/current/reference/html/)
- [Apache Kafka - Documentation](https://kafka.apache.org/documentation/)
- [Hexagonal Architecture - Alistair Cockburn](https://alistair.cockburn.us/hexagonal-architecture/)
- [Spring Boot 3 + Virtual Threads](https://spring.io/blog/2022/10/11/embracing-virtual-threads)
- [Testcontainers - Java](https://java.testcontainers.org/)
- [Flyway - Database Migrations](https://flywaydb.org/documentation/)
- [Resilience4j - Circuit Breaker](https://resilience4j.readme.io/docs/circuitbreaker)
- [Confluent Schema Registry](https://docs.confluent.io/platform/current/schema-registry/index.html)
