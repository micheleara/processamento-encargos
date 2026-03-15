package br.com.banco.processamento_encargos.adapter.in.batch;

import br.com.banco.processamento_encargos.adapter.out.s3.S3FileDownloadAdapter;
import br.com.banco.processamento_encargos.domain.model.Lancamento;
import br.com.banco.processamento_encargos.domain.model.ResultadoProcessamento;
import br.com.banco.processamento_encargos.domain.port.in.ProcessarLancamentoPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.support.SynchronizedItemStreamReader;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BatchJobConfigTest {

    @Mock
    private JobRepository jobRepository;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private DataSource dataSource;
    @Mock
    private ProcessarLancamentoPort processarLancamentoPort;
    @Mock
    private S3FileDownloadAdapter s3FileDownloadAdapter;

    private BatchJobConfig config;

    @BeforeEach
    void setUp() throws Exception {
        config = new BatchJobConfig(jobRepository, transactionManager, dataSource, processarLancamentoPort, s3FileDownloadAdapter);
        setField(config, "chunkSize", 100);
        setField(config, "partitions", 2);
    }

    private void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    @DisplayName("batchTaskExecutor deve criar SimpleAsyncTaskExecutor com virtual threads")
    void deveCriarBatchTaskExecutorComVirtualThreads() {
        TaskExecutor executor = config.batchTaskExecutor();

        assertNotNull(executor);
        assertInstanceOf(SimpleAsyncTaskExecutor.class, executor);
    }

    @Test
    @DisplayName("lancamentoProcessor deve criar LancamentoProcessor delegando ao port")
    void deveCriarLancamentoProcessor() {
        ItemProcessor<Lancamento, ResultadoProcessamento> processor = config.lancamentoProcessor();

        assertNotNull(processor);
        assertInstanceOf(LancamentoProcessor.class, processor);
    }

    @Test
    @DisplayName("s3StreamCsvReader deve criar FlatFileItemReader a partir do stream S3")
    void deveCriarS3StreamCsvReader() {
        when(s3FileDownloadAdapter.abrirStreamArquivoDoDia())
                .thenReturn(new ByteArrayInputStream("header\nrow1".getBytes()));

        FlatFileItemReader<Lancamento> reader = config.s3StreamCsvReader();

        assertNotNull(reader);
    }

    @Test
    @DisplayName("synchronizedCsvReader deve criar SynchronizedItemStreamReader envolvendo o reader S3")
    void deveCriarSynchronizedCsvReader() {
        when(s3FileDownloadAdapter.abrirStreamArquivoDoDia())
                .thenReturn(new ByteArrayInputStream("header\nrow1".getBytes()));

        SynchronizedItemStreamReader<Lancamento> reader = config.synchronizedCsvReader();

        assertNotNull(reader);
    }

    @Test
    @DisplayName("resultadoWriter deve criar JdbcBatchItemWriter configurado com DataSource")
    void deveCriarResultadoWriter() {
        JdbcBatchItemWriter<ResultadoProcessamento> writer = config.resultadoWriter();

        assertNotNull(writer);
    }

    @Test
    @DisplayName("processarLancamentosStep deve criar Step com nome correto")
    void deveCriarProcessarLancamentosStep() {
        when(s3FileDownloadAdapter.abrirStreamArquivoDoDia())
                .thenReturn(new ByteArrayInputStream("header\nrow1".getBytes()));

        Step step = config.processarLancamentosStep();

        assertNotNull(step);
        assertEquals("processarLancamentosStep", step.getName());
    }

    @Test
    @DisplayName("processarEncargosJob deve criar Job com nome correto")
    void deveCriarProcessarEncargosJob() {
        when(s3FileDownloadAdapter.abrirStreamArquivoDoDia())
                .thenReturn(new ByteArrayInputStream("header\nrow1".getBytes()));

        Job job = config.processarEncargosJob();

        assertNotNull(job);
        assertEquals("processarEncargosJob", job.getName());
    }
}