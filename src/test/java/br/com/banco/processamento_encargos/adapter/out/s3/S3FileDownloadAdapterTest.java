package br.com.banco.processamento_encargos.adapter.out.s3;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class S3FileDownloadAdapterTest {

    @Mock
    private S3Client s3Client;

    private S3FileDownloadAdapter criarAdapter(String bucketName, String s3KeyPrefix) throws Exception {
        S3Properties properties = new S3Properties();
        properties.setBucketName(bucketName);
        properties.setRegion("sa-east-1");

        S3FileDownloadAdapter adapter = new S3FileDownloadAdapter(s3Client, properties);
        setField(adapter, "s3KeyPrefix", s3KeyPrefix);
        return adapter;
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private ResponseInputStream<GetObjectResponse> mockResponseStream(String content) {
        return new ResponseInputStream<>(
                GetObjectResponse.builder().build(),
                AbortableInputStream.create(new ByteArrayInputStream(content.getBytes()))
        );
    }

    @Test
    @DisplayName("Deve abrir stream direto do S3 e retornar InputStream com conteúdo")
    void deveAbrirStreamDireto() throws Exception {
        S3FileDownloadAdapter adapter = criarAdapter("meu-bucket", "");
        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(mockResponseStream("csv-content"));

        InputStream resultado = adapter.abrirStreamArquivoDoDia();

        assertNotNull(resultado);
        assertTrue(resultado.available() > 0);

        ArgumentCaptor<GetObjectRequest> captor = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3Client).getObject(captor.capture());
        assertEquals("meu-bucket", captor.getValue().bucket());
        assertEquals("processamento/lancamentos.csv", captor.getValue().key());
    }

    @Test
    @DisplayName("Deve incluir prefixo na chave S3 quando configurado")
    void deveIncluirPrefixoNaChaveS3() throws Exception {
        S3FileDownloadAdapter adapter = criarAdapter("meu-bucket", "ambiente/");
        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("Not found").build());

        assertThrows(S3Exception.class, () -> adapter.abrirStreamArquivoDoDia());

        ArgumentCaptor<GetObjectRequest> captor = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3Client).getObject(captor.capture());
        assertEquals("ambiente/processamento/lancamentos.csv", captor.getValue().key());
    }

    @Test
    @DisplayName("Deve lançar S3Exception quando arquivo não encontrado no S3")
    void deveLancarS3ExceptionQuandoArquivoNaoEncontrado() throws Exception {
        S3FileDownloadAdapter adapter = criarAdapter("meu-bucket", "");
        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("Not found").build());

        S3Exception exception = assertThrows(S3Exception.class, () -> adapter.abrirStreamArquivoDoDia());
        assertEquals("meu-bucket", exception.getBucket());
        assertTrue(exception.getMessage().contains("não encontrado"));
    }

    @Test
    @DisplayName("Deve lançar S3Exception quando erro de rede/credenciais")
    void deveLancarS3ExceptionQuandoErroDeRede() throws Exception {
        S3FileDownloadAdapter adapter = criarAdapter("meu-bucket", "");
        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenThrow(SdkClientException.builder().message("Connection refused").build());

        S3Exception exception = assertThrows(S3Exception.class, () -> adapter.abrirStreamArquivoDoDia());
        assertTrue(exception.getMessage().contains("rede ou credenciais"));
    }

    @Test
    @DisplayName("Deve lançar S3Exception quando erro genérico do S3")
    void deveLancarS3ExceptionQuandoErroGenericoS3() throws Exception {
        S3FileDownloadAdapter adapter = criarAdapter("meu-bucket", "");
        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenThrow(software.amazon.awssdk.services.s3.model.S3Exception.builder()
                        .message("Access Denied").statusCode(403).build());

        S3Exception exception = assertThrows(S3Exception.class, () -> adapter.abrirStreamArquivoDoDia());
        assertTrue(exception.getMessage().contains("Erro ao acessar"));
    }
}