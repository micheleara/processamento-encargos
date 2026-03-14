package br.com.banco.processamento_encargos.adapter.out.s3;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class S3ExceptionTest {

    @Test
    @DisplayName("Deve criar exceção com mensagem, bucket e chave")
    void deveCriarExcecaoComMensagemBucketChave() {
        S3Exception exception = new S3Exception("Arquivo não encontrado", "meu-bucket", "processamento/lancamentos.csv");

        assertEquals("Arquivo não encontrado", exception.getMessage());
        assertEquals("meu-bucket", exception.getBucket());
        assertEquals("processamento/lancamentos.csv", exception.getChave());
        assertNull(exception.getCause());
    }

    @Test
    @DisplayName("Deve criar exceção com mensagem, bucket, chave e causa")
    void deveCriarExcecaoComCausa() {
        RuntimeException causa = new RuntimeException("Erro original");
        S3Exception exception = new S3Exception("Erro S3", "meu-bucket", "chave/arquivo.csv", causa);

        assertEquals("Erro S3", exception.getMessage());
        assertEquals("meu-bucket", exception.getBucket());
        assertEquals("chave/arquivo.csv", exception.getChave());
        assertEquals(causa, exception.getCause());
    }
}

