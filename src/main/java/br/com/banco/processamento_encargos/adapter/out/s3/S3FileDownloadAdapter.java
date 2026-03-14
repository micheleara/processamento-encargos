package br.com.banco.processamento_encargos.adapter.out.s3;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Slf4j
@Component
@RequiredArgsConstructor
public class S3FileDownloadAdapter {

    private final S3Client s3Client;
    private final S3Properties s3Properties;

    @Value("${encargos.batch.s3-key-prefix}")
    private String s3KeyPrefix;

    @Value("${encargos.batch.local-temp-dir}")
    private String localTempDir;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");


    public Path downloadArquivoDoDia() {
        return downloadArquivo(LocalDate.now());
    }


    public Path downloadArquivo(LocalDate data) {
        //Customiza o nome do arquivo com base na data, ex: lancamentos_2024-06-30.csv não aplicavel no cenário atual
        //String nomeArquivo = "lancamentos_" + data.format(DATE_FORMATTER) + ".csv";
        String nomeArquivo = "lancamentos.csv";
        String nomePasta = "processamento";
        String chaveS3 = s3KeyPrefix + nomePasta + "/" + nomeArquivo;
        String bucketName = s3Properties.getBucketName();
        Path arquivoLocal = Path.of(localTempDir, nomeArquivo);

        log.info("Iniciando download do S3: bucket={} chave={}", bucketName, chaveS3);
        long inicio = System.currentTimeMillis();

        try {
            Files.createDirectories(arquivoLocal.getParent());

            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(chaveS3)
                    .build();

            try (InputStream inputStream = s3Client.getObject(request)) {
                long bytes = Files.copy(inputStream, arquivoLocal, StandardCopyOption.REPLACE_EXISTING);
                long duracao = System.currentTimeMillis() - inicio;
                log.info("Download concluído: arquivo={} tamanho={} bytes tempo={}ms", arquivoLocal, bytes, duracao);
            }

            return arquivoLocal;

        } catch (NoSuchKeyException e) {
            log.error("Arquivo não encontrado no S3: bucket={} chave={}", bucketName, chaveS3);
            throw new S3Exception("Arquivo do dia não encontrado no bucket S3", bucketName, chaveS3, e);

        } catch (software.amazon.awssdk.services.s3.model.S3Exception e) {
            log.error("Erro de comunicação com S3: bucket={} chave={} mensagem={}", bucketName, chaveS3, e.getMessage());
            throw new S3Exception("Erro ao acessar o bucket S3", bucketName, chaveS3, e);

        } catch (SdkClientException e) {
            log.error("Erro de rede/credenciais ao acessar S3: {}", e.getMessage());
            throw new S3Exception("Erro de rede ou credenciais ao acessar S3", bucketName, chaveS3, e);

        } catch (IOException e) {
            log.error("Erro ao salvar arquivo local: {}", arquivoLocal, e);
            throw new S3Exception("Erro ao salvar arquivo baixado do S3", bucketName, chaveS3, e);
        }
    }
}

