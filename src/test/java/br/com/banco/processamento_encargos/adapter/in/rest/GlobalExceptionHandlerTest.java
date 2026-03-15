package br.com.banco.processamento_encargos.adapter.in.rest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    @Test
    @DisplayName("GlobalExceptionHandler deve ser instanciado sem erros")
    void deveInstanciarSemErros() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        assertNotNull(handler);
    }
}