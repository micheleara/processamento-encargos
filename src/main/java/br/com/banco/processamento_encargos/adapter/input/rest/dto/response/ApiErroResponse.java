package br.com.banco.processamento_encargos.adapter.input.rest.dto.response;


public record ApiErroResponse(String codigoErro, String descricaoErro, Object dadosDoErro) {

}
