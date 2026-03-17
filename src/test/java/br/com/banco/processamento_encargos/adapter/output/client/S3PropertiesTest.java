package br.com.banco.processamento_encargos.adapter.output.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class S3PropertiesTest {

    @Test
    @DisplayName("S3Properties deve permitir leitura e escrita de todos os campos")
    void devePermitirLeituraEEscritaDeCampos() {
        S3Properties properties = new S3Properties();
        properties.setAccessKey("my-access-key");
        properties.setSecretKey("my-secret-key");
        properties.setRegion("sa-east-1");
        properties.setBucketName("my-bucket");

        assertEquals("my-access-key", properties.getAccessKey());
        assertEquals("my-secret-key", properties.getSecretKey());
        assertEquals("sa-east-1", properties.getRegion());
        assertEquals("my-bucket", properties.getBucketName());
    }
}