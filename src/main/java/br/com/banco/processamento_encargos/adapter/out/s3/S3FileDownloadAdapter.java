package br.com.banco.processamento_encargos.adapter.out.s3;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.io.InputStream;

@Slf4j
@Component
@RequiredArgsConstructor
public class S3FileDownloadAdapter {

    private final S3Client s3Client;
    private final S3Properties s3Properties;

    @Value("${encargos.batch.s3-key-prefix}")
    private String s3KeyPrefix;

    /**
     * Abre um InputStream direto do S3 sem salvar em disco.
     * O chamador é responsável por fechar o stream após o uso.
     */
    public InputStream abrirStreamArquivoDoDia() {
        String chaveS3 = s3KeyPrefix + "processamento/lancamentos.csv";
        String bucketName = s3Properties.getBucketName();

        log.info("Abrindo stream direto do S3: bucket={} chave={}", bucketName, chaveS3);

        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(chaveS3)
                    .build();

            ResponseInputStream<GetObjectResponse> response = s3Client.getObject(request);
            log.info("Stream S3 aberto com sucesso: content-length={} content-type={}",
                    response.response().contentLength(), response.response().contentType());
            return response;

        } catch (NoSuchKeyException e) {
            log.error("Arquivo não encontrado no S3: bucket={} chave={}", bucketName, chaveS3);
            throw new S3Exception("Arquivo do dia não encontrado no bucket S3", bucketName, chaveS3, e);

        } catch (software.amazon.awssdk.services.s3.model.S3Exception e) {
            log.error("Erro de comunicação com S3: bucket={} chave={} mensagem={}", bucketName, chaveS3, e.getMessage());
            throw new S3Exception("Erro ao acessar o bucket S3", bucketName, chaveS3, e);

        } catch (SdkClientException e) {
            log.error("Erro de rede/credenciais ao acessar S3: {}", e.getMessage());
            throw new S3Exception("Erro de rede ou credenciais ao acessar S3", bucketName, chaveS3, e);
        }
    }
}