package br.com.banco.processamento_encargos.adapter.input.batch;

import br.com.banco.processamento_encargos.core.domain.model.Lancamento;
import br.com.banco.processamento_encargos.core.domain.model.TipoLancamento;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.item.file.transform.DefaultFieldSet;
import org.springframework.batch.item.file.transform.FieldSet;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class LancamentoCsvFieldSetMapperTest {

    private LancamentoCsvFieldSetMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new LancamentoCsvFieldSetMapper();
    }

    private FieldSet criarFieldSet(String id, String conta, String tipo, String valor, String data, String descricao, String evento) {
        String[] names = {"idLancamento", "numeroConta", "tipoLancamento", "valor", "dataLancamento", "descricao", "evento"};
        String[] values = {id, conta, tipo, valor, data, descricao, evento};
        return new DefaultFieldSet(values, names);
    }

    @Test
    @DisplayName("Deve mapear FieldSet de Debito corretamente para Lancamento")
    void deveMapearDebito() throws Exception {
        FieldSet fieldSet = criarFieldSet(
                "abc-123", "001234567-8", "Debito", "150.75", "10/03/2026", "Taxa mensal", "Debitar");

        Lancamento resultado = mapper.mapFieldSet(fieldSet);

        assertEquals("abc-123", resultado.idLancamento());
        assertEquals("001234567-8", resultado.numeroConta());
        assertEquals(TipoLancamento.DEBITO, resultado.tipoLancamento());
        assertEquals(new BigDecimal("150.75"), resultado.valor());
        assertEquals(LocalDate.of(2026, 3, 10), resultado.dataLancamento());
        assertEquals("Taxa mensal", resultado.descricao());
        assertEquals("Debitar", resultado.evento());
    }

    @Test
    @DisplayName("Deve mapear FieldSet de Credito corretamente para Lancamento")
    void deveMapearCredito() throws Exception {
        FieldSet fieldSet = criarFieldSet(
                "def-456", "009876543-2", "Credito", "5000.00", "15/01/2026", "Estorno", "Creditar");

        Lancamento resultado = mapper.mapFieldSet(fieldSet);

        assertEquals("def-456", resultado.idLancamento());
        assertEquals("009876543-2", resultado.numeroConta());
        assertEquals(TipoLancamento.CREDITO, resultado.tipoLancamento());
        assertEquals(new BigDecimal("5000.00"), resultado.valor());
        assertEquals(LocalDate.of(2026, 1, 15), resultado.dataLancamento());
        assertEquals("Estorno", resultado.descricao());
        assertEquals("Creditar", resultado.evento());
    }

    @Test
    @DisplayName("Deve mapear valor com muitas casas decimais corretamente")
    void deveMapearValorComMuitasCasasDecimais() throws Exception {
        FieldSet fieldSet = criarFieldSet(
                "ghi-789", "001111111-1", "DEBITO", "0.01", "30/06/2026", "Centavo", "Debitar");

        Lancamento resultado = mapper.mapFieldSet(fieldSet);

        assertEquals(new BigDecimal("0.01"), resultado.valor());
    }

    @Test
    @DisplayName("Deve lançar exceção para tipo de lançamento inválido")
    void deveLancarExcecaoParaTipoInvalido() {
        FieldSet fieldSet = criarFieldSet(
                "xyz-000", "001111111-1", "INVALIDO", "100.00", "10/03/2026", "Teste", "Teste");

        assertThrows(IllegalArgumentException.class, () -> mapper.mapFieldSet(fieldSet));
    }

    @Test
    @DisplayName("Deve lançar exceção para data com formato inválido")
    void deveLancarExcecaoParaDataInvalida() {
        FieldSet fieldSet = criarFieldSet(
                "xyz-000", "001111111-1", "DEBITO", "100.00", "2026-03-13", "Teste", "Debitar");

        assertThrows(Exception.class, () -> mapper.mapFieldSet(fieldSet));
    }

    @Test
    @DisplayName("Deve lançar exceção para valor não numérico")
    void deveLancarExcecaoParaValorNaoNumerico() {
        FieldSet fieldSet = criarFieldSet(
                "xyz-000", "001111111-1", "DEBITO", "abc", "10/03/2026", "Teste", "Debitar");

        assertThrows(NumberFormatException.class, () -> mapper.mapFieldSet(fieldSet));
    }
}
