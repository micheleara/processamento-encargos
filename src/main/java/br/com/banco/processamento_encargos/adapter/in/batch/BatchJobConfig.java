package br.com.banco.processamento_encargos.adapter.in.batch;

import br.com.banco.processamento_encargos.adapter.out.s3.S3FileDownloadAdapter;
import br.com.banco.processamento_encargos.domain.model.Lancamento;
import br.com.banco.processamento_encargos.domain.model.ResultadoProcessamento;
import br.com.banco.processamento_encargos.domain.port.in.ProcessarLancamentoPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class BatchJobConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final DataSource dataSource;
    private final ProcessarLancamentoPort processarLancamentoPort;
    private final S3FileDownloadAdapter s3FileDownloadAdapter;

    @Value("${encargos.batch.chunk-size:1000}")
    private int chunkSize;

    @Value("${encargos.batch.partitions:10}")
    private int partitions;

    @Bean
    public Job processarEncargosJob() {
        return new JobBuilder("processarEncargosJob", jobRepository)
                .start(downloadArquivoS3Step())
                .next(partitionedProcessarStep())
                .listener(cleanupJobListener())
                .build();
    }

    @Bean
    public Step downloadArquivoS3Step() {
        return new StepBuilder("downloadArquivoS3Step", jobRepository)
                .tasklet(s3DownloadTasklet(), transactionManager)
                .build();
    }

    @Bean
    public S3DownloadTasklet s3DownloadTasklet() {
        return new S3DownloadTasklet(s3FileDownloadAdapter);
    }

    @Bean
    public Step partitionedProcessarStep() {
        return new StepBuilder("partitionedProcessarStep", jobRepository)
                .partitioner("processarLancamentosStep", partitioner(null))
                .step(processarLancamentosStep())
                .gridSize(partitions)
                .taskExecutor(batchTaskExecutor())
                .build();
    }

    @Bean
    @StepScope
    public Partitioner partitioner(
            @Value("#{jobExecutionContext['csvFilePath']}") String csvFilePath) {
        if (csvFilePath == null) {
            // Durante a criação do bean, o path ainda não está disponível
            return gridSize -> java.util.Collections.emptyMap();
        }
        return new LancamentoPartitioner(Path.of(csvFilePath), partitions);
    }

    @Bean
    public Step processarLancamentosStep() {
        return new StepBuilder("processarLancamentosStep", jobRepository)
                .<Lancamento, ResultadoProcessamento>chunk(chunkSize, transactionManager)
                .reader(csvReader(null, null, null))
                .processor(lancamentoProcessor())
                .writer(resultadoWriter())
                .faultTolerant()
                .skipLimit(10_000)
                .skip(Exception.class)
                .build();
    }

    @Bean
    @StepScope
    public FlatFileItemReader<Lancamento> csvReader(
            @Value("#{stepExecutionContext['filePath']}") String filePath,
            @Value("#{stepExecutionContext['startLine']}") Integer startLine,
            @Value("#{stepExecutionContext['maxItems']}") Integer maxItems) {

        FlatFileItemReaderBuilder<Lancamento> builder = new FlatFileItemReaderBuilder<Lancamento>()
                .name("lancamentoCsvReader")
                .linesToSkip(1) // pular cabeçalho
                .delimited()
                .names("idLancamento", "numeroConta", "tipoLancamento", "valor", "dataLancamento", "descricao")
                .fieldSetMapper(new LancamentoCsvFieldSetMapper());

        if (filePath != null) {
            builder.resource(new FileSystemResource(filePath));
        }
        if (startLine != null && startLine > 0) {
            builder.currentItemCount(startLine);
        }
        if (maxItems != null && maxItems > 0) {
            builder.maxItemCount((startLine != null ? startLine : 0) + maxItems);
        }

        return builder.build();
    }

    @Bean
    public ItemProcessor<Lancamento, ResultadoProcessamento> lancamentoProcessor() {
        return new LancamentoProcessor(processarLancamentoPort);
    }

    @Bean
    public JdbcBatchItemWriter<ResultadoProcessamento> resultadoWriter() {
        return new JdbcBatchItemWriterBuilder<ResultadoProcessamento>()
                .dataSource(dataSource)
                .sql("""
                    INSERT INTO resultado_processamento 
                        (id_lancamento, numero_conta, tipo_lancamento, valor, data_lancamento, descricao, status, motivo_rejeicao, data_processamento)
                    VALUES 
                        (:idLancamento, :numeroConta, :tipoLancamento, :valor, :dataLancamento, :descricao, :status, :motivoRejeicao, :dataProcessamento)
                    ON CONFLICT (id_lancamento) DO NOTHING
                    """)
                .beanMapped()
                .build();
    }

    @Bean
    public TaskExecutor batchTaskExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("batch-encargos-");
        executor.setConcurrencyLimit(partitions);
        executor.setVirtualThreads(true); // Java 21 Virtual Threads
        return executor;
    }

    @Bean
    public JobExecutionListener cleanupJobListener() {
        return new JobExecutionListener() {
            @Override
            public void afterJob(JobExecution jobExecution) {
                String csvFilePath = jobExecution.getExecutionContext().getString("csvFilePath", null);
                if (csvFilePath != null) {
                    try {
                        Path path = Path.of(csvFilePath);
                        if (Files.deleteIfExists(path)) {
                            log.info("Arquivo temporário removido: {}", csvFilePath);
                        }
                    } catch (Exception e) {
                        log.warn("Falha ao remover arquivo temporário: {}", csvFilePath, e);
                    }
                }
                log.info("Job finalizado com status: {}", jobExecution.getStatus());
            }
        };
    }
}
