package br.com.banco.processamento_encargos.adapter.in.batch;

import br.com.banco.processamento_encargos.adapter.out.s3.S3FileDownloadAdapter;
import br.com.banco.processamento_encargos.domain.model.Lancamento;
import br.com.banco.processamento_encargos.domain.model.ResultadoProcessamento;
import br.com.banco.processamento_encargos.domain.port.in.ProcessarLancamentoPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.support.SynchronizedItemStreamReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.InputStream;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class BatchJobConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final ProcessarLancamentoPort processarLancamentoPort;
    private final S3FileDownloadAdapter s3FileDownloadAdapter;

    @Value("${encargos.batch.chunk-size:1000}")
    private int chunkSize;

    @Value("${encargos.batch.partitions:10}")
    private int partitions;

    @Bean
    public Job processarEncargosJob() {
        return new JobBuilder("processarEncargosJob", jobRepository)
                .start(processarLancamentosStep())
                .build();
    }

    /**
     * Step único com leitura via stream S3 e processamento multi-threaded.
     * O reader é sincronizado (SynchronizedItemStreamReader) para thread-safety.
     * O taskExecutor permite que múltiplos chunks sejam processados em paralelo.
     */
    @Bean
    public Step processarLancamentosStep() {
        return new StepBuilder("processarLancamentosStep", jobRepository)
                .<Lancamento, ResultadoProcessamento>chunk(chunkSize, transactionManager)
                .reader(synchronizedCsvReader())
                .processor(lancamentoProcessor())
                .writer(resultadoWriter())
                .taskExecutor(batchTaskExecutor())
                .faultTolerant()
                .skipLimit(10_000)
                .skip(Exception.class)
                .build();
    }

    /**
     * Reader sincronizado que envolve o FlatFileItemReader para uso em step multi-threaded.
     * Garante que apenas uma thread leia do stream S3 por vez.
     * @StepScope garante que uma nova instância (e novo stream S3) seja criada a cada execução do step.
     */
    @Bean
    @StepScope
    public SynchronizedItemStreamReader<Lancamento> synchronizedCsvReader() {
        SynchronizedItemStreamReader<Lancamento> synchronizedReader = new SynchronizedItemStreamReader<>();
        synchronizedReader.setDelegate(s3StreamCsvReader());
        return synchronizedReader;
    }

    /**
     * FlatFileItemReader que lê diretamente do stream S3 via InputStreamResource.
     * Elimina o download do arquivo para disco, evitando gargalo de I/O e consumo de espaço em /tmp.
     * @StepScope garante que o stream S3 seja aberto apenas no momento da execução do step.
     */
    @Bean
    @StepScope
    public FlatFileItemReader<Lancamento> s3StreamCsvReader() {
        InputStream s3Stream = s3FileDownloadAdapter.abrirStreamArquivoDoDia();

        return new FlatFileItemReaderBuilder<Lancamento>()
                .name("lancamentoCsvReader")
                .resource(new InputStreamResource(s3Stream, "S3 CSV Stream - lancamentos.csv"))
                .linesToSkip(1)
                .delimited()
                .names("idLancamento", "numeroConta", "tipoLancamento", "valor", "dataLancamento", "descricao", "evento")
                .fieldSetMapper(new LancamentoCsvFieldSetMapper())
                .saveState(false)
                .build();
    }

    @Bean
    public ItemProcessor<Lancamento, ResultadoProcessamento> lancamentoProcessor() {
        return new LancamentoProcessor(processarLancamentoPort);
    }

    @Bean
    public ItemWriter<ResultadoProcessamento> resultadoWriter() {
        // A persistência é feita pelo ProcessarLancamentoService via SalvarResultadoProcessamentoPort
        return items -> {};
    }

    @Bean
    public TaskExecutor batchTaskExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("batch-encargos-");
        executor.setConcurrencyLimit(partitions);
        executor.setVirtualThreads(true);
        return executor;
    }
}