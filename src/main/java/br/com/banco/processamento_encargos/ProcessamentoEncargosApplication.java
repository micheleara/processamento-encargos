package br.com.banco.processamento_encargos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ProcessamentoEncargosApplication {

	public static void main(String[] args) {
		SpringApplication.run(ProcessamentoEncargosApplication.class, args);
	}

}
