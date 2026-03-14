package br.com.banco.processamento_encargos.adapter.in.batch;

import br.com.banco.processamento_encargos.adapter.out.s3.S3FileDownloadAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.*;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.repeat.RepeatStatus;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class S3DownloadTaskletTest {

    @Mock
    private S3FileDownloadAdapter s3FileDownloadAdapter;

    private S3DownloadTasklet tasklet;

    @BeforeEach
    void setUp() {
        tasklet = new S3DownloadTasklet(s3FileDownloadAdapter);
    }

    private StepContribution criarStepContribution() {
        JobExecution jobExecution = new JobExecution(1L, new JobParameters());
        StepExecution stepExecution = new StepExecution("testStep", jobExecution);
        return new StepContribution(stepExecution);
    }

    private ChunkContext criarChunkContext(StepContribution contribution) {
        StepContext stepContext = new StepContext(contribution.getStepExecution());
        return new ChunkContext(stepContext);
    }

    @Test
    @DisplayName("Deve baixar arquivo e armazenar caminho no ExecutionContext do Job")
    void deveBaixarArquivoEArmazenarCaminho() throws Exception {
        Path arquivoSimulado = Path.of("/tmp/encargos/lancamentos.csv");
        when(s3FileDownloadAdapter.downloadArquivoDoDia()).thenReturn(arquivoSimulado);

        StepContribution contribution = criarStepContribution();
        ChunkContext chunkContext = criarChunkContext(contribution);

        RepeatStatus status = tasklet.execute(contribution, chunkContext);

        assertEquals(RepeatStatus.FINISHED, status);
        String csvFilePath = contribution.getStepExecution()
                .getJobExecution()
                .getExecutionContext()
                .getString("csvFilePath");
        assertEquals(arquivoSimulado.toAbsolutePath().toString(), csvFilePath);
        verify(s3FileDownloadAdapter, times(1)).downloadArquivoDoDia();
    }

    @Test
    @DisplayName("Deve propagar exceção quando o download falhar")
    void devePropagarExcecaoQuandoDownloadFalhar() {
        when(s3FileDownloadAdapter.downloadArquivoDoDia())
                .thenThrow(new RuntimeException("Erro S3"));

        StepContribution contribution = criarStepContribution();
        ChunkContext chunkContext = criarChunkContext(contribution);

        assertThrows(RuntimeException.class, () -> tasklet.execute(contribution, chunkContext));
        verify(s3FileDownloadAdapter, times(1)).downloadArquivoDoDia();
    }
}

