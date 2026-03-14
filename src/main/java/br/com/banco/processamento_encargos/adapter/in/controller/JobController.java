package br.com.banco.processamento_encargos.adapter.in.controller;


import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequiredArgsConstructor
public class JobController {

    private final JobLauncher jobLauncher;
    private final Job processarEncargosJob;

    @PostMapping("/api/v1/batch/executar")
    public ResponseEntity<String> executarJob() throws Exception {
        var params = new JobParametersBuilder()
                .addString("executedAt", LocalDateTime.now().toString())
                .toJobParameters();
        var execution = jobLauncher.run(processarEncargosJob, params);
        return ResponseEntity.ok("Job disparado. Status: " + execution.getStatus());
    }
}
