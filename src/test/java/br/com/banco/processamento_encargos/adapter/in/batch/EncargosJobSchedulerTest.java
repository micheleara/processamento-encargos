package br.com.banco.processamento_encargos.adapter.in.batch;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.launch.JobLauncher;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EncargosJobSchedulerTest {

    @Mock
    private JobLauncher jobLauncher;

    @Mock
    private Job processarEncargosJob;

    private EncargosJobScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new EncargosJobScheduler(jobLauncher, processarEncargosJob);
    }

    @Test
    @DisplayName("Deve disparar o job com parâmetro executedAt")
    void deveDispararJobComParametros() throws Exception {
        JobExecution jobExecution = new JobExecution(1L, new JobParameters());
        when(jobLauncher.run(eq(processarEncargosJob), any(JobParameters.class))).thenReturn(jobExecution);

        scheduler.dispararJob();

        ArgumentCaptor<JobParameters> captor = ArgumentCaptor.forClass(JobParameters.class);
        verify(jobLauncher).run(eq(processarEncargosJob), captor.capture());
        assertNotNull(captor.getValue().getString("executedAt"));
    }

    @Test
    @DisplayName("Deve capturar exceção sem propagar quando job falha")
    void deveCapturarExcecaoQuandoJobFalha() throws Exception {
        when(jobLauncher.run(eq(processarEncargosJob), any(JobParameters.class)))
                .thenThrow(new RuntimeException("Erro no job"));

        assertDoesNotThrow(() -> scheduler.dispararJob());
        verify(jobLauncher).run(eq(processarEncargosJob), any(JobParameters.class));
    }
}

