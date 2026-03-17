package br.com.banco.processamento_encargos.adapter.input.batch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class EncargosJobScheduler {

    private final JobLauncher jobLauncher;
    private final Job processarEncargosJob;

    @Scheduled(cron = "0 0 4 * * *")
    public void dispararJob() {
        try {
            LocalDateTime inicio = LocalDateTime.now();
            log.info("Iniciando job de processamento de encargos às {}", inicio);

            JobParameters params = new JobParametersBuilder()
                    .addString("executedAt", inicio.toString())
                    .toJobParameters();

            jobLauncher.run(processarEncargosJob, params);

            log.info("Job finalizado. Início: {} | Fim: {} | Duração: {}ms",
                    inicio, LocalDateTime.now(),
                    java.time.Duration.between(inicio, LocalDateTime.now()).toMillis());

        } catch (Exception e) {
            log.error("Erro ao executar job de processamento de encargos", e);
        }
    }
}
