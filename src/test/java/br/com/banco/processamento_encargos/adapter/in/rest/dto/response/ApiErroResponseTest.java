package br.com.banco.processamento_encargos.adapter.in.rest.dto.response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ApiErroResponseTest {

    @Test
    @DisplayName("Deve criar ApiErroResponse com todos os campos")
    void deveCriarComTodosOsCampos() {
        ApiErroResponse response = new ApiErroResponse("ERR-001", "Erro de validação", List.of("campo obrigatório"));

        assertEquals("ERR-001", response.codigoErro());
        assertEquals("Erro de validação", response.descricaoErro());
        assertEquals(List.of("campo obrigatório"), response.dadosDoErro());
    }

    @Test
    @DisplayName("Deve aceitar dadosDoErro nulo")
    void deveAceitarDadosDoErroNulo() {
        ApiErroResponse response = new ApiErroResponse("ERR-002", "Erro interno", null);

        assertEquals("ERR-002", response.codigoErro());
        assertEquals("Erro interno", response.descricaoErro());
        assertNull(response.dadosDoErro());
    }

    @Test
    @DisplayName("Dois registros com mesmos valores devem ser iguais")
    void doisRegistrosComMesmosValoresDevemSerIguais() {
        ApiErroResponse r1 = new ApiErroResponse("ERR-003", "Descrição", "dado");
        ApiErroResponse r2 = new ApiErroResponse("ERR-003", "Descrição", "dado");

        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
    }

    @Test
    @DisplayName("toString deve conter os valores dos campos")
    void toStringDeveConterValores() {
        ApiErroResponse response = new ApiErroResponse("ERR-004", "Mensagem", null);

        String str = response.toString();

        assertTrue(str.contains("ERR-004"));
        assertTrue(str.contains("Mensagem"));
    }
}