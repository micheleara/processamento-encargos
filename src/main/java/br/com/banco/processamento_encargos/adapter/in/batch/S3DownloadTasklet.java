package br.com.banco.processamento_encargos.adapter.in.batch;

import br.com.banco.processamento_encargos.adapter.out.s3.S3FileDownloadAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;

import java.nio.file.Path;

@Slf4j
@RequiredArgsConstructor
public class S3DownloadTasklet implements Tasklet {

    private final S3FileDownloadAdapter s3FileDownloadAdapter;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        log.info("Iniciando download do arquivo CSV do S3...");

        Path arquivoLocal = s3FileDownloadAdapter.downloadArquivoDoDia();

        contribution.getStepExecution()
                .getJobExecution()
                .getExecutionContext()
                .putString("csvFilePath", arquivoLocal.toAbsolutePath().toString());

        log.info("Download concluído. Arquivo salvo em: {}", arquivoLocal.toAbsolutePath());
        return RepeatStatus.FINISHED;
    }
}

