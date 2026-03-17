package br.com.banco.processamento_encargos.adapter.input.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.*;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobControllerTest {

    @Mock
    private JobLauncher jobLauncher;

    @Mock
    private Job processarEncargosJob;

    private JobController controller;

    @BeforeEach
    void setUp() {
        controller = new JobController(jobLauncher, processarEncargosJob);
    }

    @Test
    @DisplayName("Deve executar job e retornar status 200 com mensagem de sucesso")
    void deveExecutarJobComSucesso() throws Exception {
        JobExecution jobExecution = new JobExecution(1L, new JobParameters());
        jobExecution.setStatus(BatchStatus.COMPLETED);
        when(jobLauncher.run(eq(processarEncargosJob), any(JobParameters.class))).thenReturn(jobExecution);

        ResponseEntity<String> response = controller.executarJob();

        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().contains("COMPLETED"));
        verify(jobLauncher).run(eq(processarEncargosJob), any(JobParameters.class));
    }

    @Test
    @DisplayName("Deve propagar exceção quando job launcher falha")
    void devePropagarExcecaoQuandoJobFalha() throws Exception {
        when(jobLauncher.run(eq(processarEncargosJob), any(JobParameters.class)))
                .thenThrow(new RuntimeException("Erro ao iniciar job"));

        assertThrows(RuntimeException.class, () -> controller.executarJob());
    }
}
