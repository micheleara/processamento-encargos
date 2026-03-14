package br.com.banco.processamento_encargos.adapter.out.s3;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
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
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class S3FileDownloadAdapterTest {

    @Mock
    private S3Client s3Client;

    @TempDir
    Path tempDir;

    private S3FileDownloadAdapter criarAdapter(String bucketName, String s3KeyPrefix) throws Exception {
        S3Properties properties = new S3Properties();
        properties.setBucketName(bucketName);
        properties.setRegion("sa-east-1");

        S3FileDownloadAdapter adapter = new S3FileDownloadAdapter(s3Client, properties);

        setField(adapter, "s3KeyPrefix", s3KeyPrefix);
        setField(adapter, "localTempDir", tempDir.toString());

        return adapter;
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    @DisplayName("Deve construir chave S3 corretamente e fazer download com sucesso")
    void deveConstruirChaveS3CorretamenteEFazerDownload() throws Exception {
        S3FileDownloadAdapter adapter = criarAdapter("meu-bucket", "");

        ByteArrayInputStream content = new ByteArrayInputStream("id,conta,tipo\nabc,123,DEBITO".getBytes());
        ResponseInputStream<GetObjectResponse> responseStream = new ResponseInputStream<>(
                GetObjectResponse.builder().build(),
                AbortableInputStream.create(content)
        );
        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(responseStream);

        Path resultado = adapter.downloadArquivo(LocalDate.of(2026, 3, 13));

        assertNotNull(resultado);
        assertTrue(resultado.toString().contains("lancamentos.csv"));

        ArgumentCaptor<GetObjectRequest> captor = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3Client).getObject(captor.capture());
        assertEquals("meu-bucket", captor.getValue().bucket());
        assertEquals("processamento/lancamentos.csv", captor.getValue().key());
    }

    @Test
    @DisplayName("Deve lançar S3Exception quando arquivo não encontrado no S3")
    void deveLancarS3ExceptionQuandoArquivoNaoEncontrado() throws Exception {
        S3FileDownloadAdapter adapter = criarAdapter("meu-bucket", "");

        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("Not found").build());

        S3Exception exception = assertThrows(S3Exception.class, () -> adapter.downloadArquivo(LocalDate.now()));
        assertEquals("meu-bucket", exception.getBucket());
        assertTrue(exception.getMessage().contains("não encontrado"));
    }

    @Test
    @DisplayName("Deve lançar S3Exception quando erro de rede/credenciais")
    void deveLancarS3ExceptionQuandoErroDeRede() throws Exception {
        S3FileDownloadAdapter adapter = criarAdapter("meu-bucket", "");

        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenThrow(SdkClientException.builder().message("Connection refused").build());

        S3Exception exception = assertThrows(S3Exception.class, () -> adapter.downloadArquivo(LocalDate.now()));
        assertTrue(exception.getMessage().contains("rede ou credenciais"));
    }

    @Test
    @DisplayName("Deve lançar S3Exception quando erro genérico do S3")
    void deveLancarS3ExceptionQuandoErroGenericoS3() throws Exception {
        S3FileDownloadAdapter adapter = criarAdapter("meu-bucket", "");

        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenThrow(software.amazon.awssdk.services.s3.model.S3Exception.builder()
                        .message("Access Denied").statusCode(403).build());

        S3Exception exception = assertThrows(S3Exception.class, () -> adapter.downloadArquivo(LocalDate.now()));
        assertTrue(exception.getMessage().contains("Erro ao acessar"));
    }

    @Test
    @DisplayName("downloadArquivoDoDia deve delegar para downloadArquivo com data atual")
    void downloadArquivoDoDiaDeveDelegarParaDownloadArquivo() throws Exception {
        S3FileDownloadAdapter adapter = criarAdapter("meu-bucket", "");

        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("Not found").build());

        assertThrows(S3Exception.class, () -> adapter.downloadArquivoDoDia());
        verify(s3Client, times(1)).getObject(any(GetObjectRequest.class));
    }

    @Test
    @DisplayName("Deve incluir prefixo na chave S3 quando configurado")
    void deveIncluirPrefixoNaChaveS3() throws Exception {
        S3FileDownloadAdapter adapter = criarAdapter("meu-bucket", "ambiente/");

        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("Not found").build());

        assertThrows(S3Exception.class, () -> adapter.downloadArquivo(LocalDate.now()));

        ArgumentCaptor<GetObjectRequest> captor = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3Client).getObject(captor.capture());
        assertEquals("ambiente/processamento/lancamentos.csv", captor.getValue().key());
    }
}
