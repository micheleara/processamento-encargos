package br.com.banco.processamento_encargos.adapter.out.s3;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;


@Configuration
@EnableConfigurationProperties(S3Properties.class)
public class S3ClientConfig {

    @Bean
    public S3Client s3Client(S3Properties properties) {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                properties.getAccessKey(), properties.getSecretKey());

        return S3Client.builder()
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .build();
    }
}

