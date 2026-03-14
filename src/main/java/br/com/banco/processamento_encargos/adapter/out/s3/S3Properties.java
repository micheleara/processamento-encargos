package br.com.banco.processamento_encargos.adapter.out.s3;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "cloud.aws.s3")
public class S3Properties {

    private String accessKey;
    private String secretKey;
    private String region;
    private String bucketName;
}

