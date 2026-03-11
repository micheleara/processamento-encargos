package br.com.banco.processamento_encargos;

import br.com.banco.processamento_encargos.domain.exemplo;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ProcessamentoEncargosApplicationTests {

	@Test
	void contextLoads() {
	}

	@Test
	void applicationMainRuns() {
		ProcessamentoEncargosApplication.main(new String[]{"--spring.main.web-application-type=none"});
	}

	@Test
	void exemploMainRuns() {
		exemplo.main(new String[]{});
	}

}