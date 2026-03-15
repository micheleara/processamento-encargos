package br.com.banco.processamento_encargos;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
		"spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration",
		"spring.flyway.enabled=false",
		"spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"spring.batch.jdbc.initialize-schema=always",
		"spring.batch.job.enabled=false",
		"encargos.kafka.topics.consulta-request=encargos.conta.consulta.request",
		"encargos.kafka.topics.consulta-response=encargos.conta.consulta.response",
		"encargos.kafka.consulta-timeout-seconds=5",
		"cloud.aws.s3.access-key=test",
		"cloud.aws.s3.secret-key=test",
		"cloud.aws.s3.region=us-east-1",
		"cloud.aws.s3.bucket-name=test-bucket",
		"spring.profiles.active=local"
})
class ProcessamentoEncargosApplicationTests {

	@Test
	void contextLoads() {
	}
}