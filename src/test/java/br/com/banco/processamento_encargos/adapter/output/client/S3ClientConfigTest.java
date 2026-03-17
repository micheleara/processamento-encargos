package br.com.banco.processamento_encargos.adapter.output.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;

import static org.junit.jupiter.api.Assertions.*;

class S3ClientConfigTest {

    @Test
    @DisplayName("s3Client deve criar instância S3Client com as credenciais fornecidas")
    void deveCriarS3ClientComCredenciais() {
        S3ClientConfig config = new S3ClientConfig();
        S3Properties properties = new S3Properties();
        properties.setAccessKey("test-access-key");
        properties.setSecretKey("test-secret-key");
        properties.setRegion("us-east-1");
        properties.setBucketName("test-bucket");

        S3Client client = config.s3Client(properties);

        assertNotNull(client);
        client.close();
    }
}