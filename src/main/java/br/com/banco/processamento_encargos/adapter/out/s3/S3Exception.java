package br.com.banco.processamento_encargos.adapter.out.s3;


public class S3Exception extends RuntimeException {

    private final String bucket;
    private final String chave;

    public S3Exception(String mensagem, String bucket, String chave) {
        super(mensagem);
        this.bucket = bucket;
        this.chave = chave;
    }

    public S3Exception(String mensagem, String bucket, String chave, Throwable causa) {
        super(mensagem, causa);
        this.bucket = bucket;
        this.chave = chave;
    }

    public String getBucket() { return bucket; }
    public String getChave() { return chave; }
}

