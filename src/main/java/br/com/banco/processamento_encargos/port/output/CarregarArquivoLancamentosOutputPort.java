package br.com.banco.processamento_encargos.port.output;

import java.io.InputStream;

public interface CarregarArquivoLancamentosOutputPort {

    InputStream abrirStreamArquivoDoDia();
}