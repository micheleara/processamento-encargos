package br.com.banco.processamento_encargos.adapter.in.batch;

import br.com.banco.processamento_encargos.domain.model.Lancamento;
import br.com.banco.processamento_encargos.domain.model.TipoLancamento;
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

    private FieldSet criarFieldSet(String id, String conta, String tipo, String valor, String data, String descricao) {
        String[] names = {"idLancamento", "numeroConta", "tipoLancamento", "valor", "dataLancamento", "descricao"};
        String[] values = {id, conta, tipo, valor, data, descricao};
        return new DefaultFieldSet(values, names);
    }

    @Test
    @DisplayName("Deve mapear FieldSet de DEBITO corretamente para Lancamento")
    void deveMapearDebito() throws Exception {
        FieldSet fieldSet = criarFieldSet(
                "abc-123", "001234567-8", "DEBITO", "150.75", "2026-03-10", "Taxa mensal");

        Lancamento resultado = mapper.mapFieldSet(fieldSet);

        assertEquals("abc-123", resultado.idLancamento());
        assertEquals("001234567-8", resultado.numeroConta());
        assertEquals(TipoLancamento.DEBITO, resultado.tipoLancamento());
        assertEquals(new BigDecimal("150.75"), resultado.valor());
        assertEquals(LocalDate.of(2026, 3, 10), resultado.dataLancamento());
        assertEquals("Taxa mensal", resultado.descricao());
    }

    @Test
    @DisplayName("Deve mapear FieldSet de CREDITO corretamente para Lancamento")
    void deveMapearCredito() throws Exception {
        FieldSet fieldSet = criarFieldSet(
                "def-456", "009876543-2", "CREDITO", "5000.00", "2026-01-15", "Estorno");

        Lancamento resultado = mapper.mapFieldSet(fieldSet);

        assertEquals("def-456", resultado.idLancamento());
        assertEquals("009876543-2", resultado.numeroConta());
        assertEquals(TipoLancamento.CREDITO, resultado.tipoLancamento());
        assertEquals(new BigDecimal("5000.00"), resultado.valor());
        assertEquals(LocalDate.of(2026, 1, 15), resultado.dataLancamento());
        assertEquals("Estorno", resultado.descricao());
    }

    @Test
    @DisplayName("Deve mapear valor com muitas casas decimais corretamente")
    void deveMapearValorComMuitasCasasDecimais() throws Exception {
        FieldSet fieldSet = criarFieldSet(
                "ghi-789", "001111111-1", "DEBITO", "0.01", "2026-06-30", "Centavo");

        Lancamento resultado = mapper.mapFieldSet(fieldSet);

        assertEquals(new BigDecimal("0.01"), resultado.valor());
    }

    @Test
    @DisplayName("Deve lançar exceção para tipo de lançamento inválido")
    void deveLancarExcecaoParaTipoInvalido() {
        FieldSet fieldSet = criarFieldSet(
                "xyz-000", "001111111-1", "INVALIDO", "100.00", "2026-03-10", "Teste");

        assertThrows(IllegalArgumentException.class, () -> mapper.mapFieldSet(fieldSet));
    }

    @Test
    @DisplayName("Deve lançar exceção para data com formato inválido")
    void deveLancarExcecaoParaDataInvalida() {
        FieldSet fieldSet = criarFieldSet(
                "xyz-000", "001111111-1", "DEBITO", "100.00", "13/03/2026", "Teste");

        assertThrows(Exception.class, () -> mapper.mapFieldSet(fieldSet));
    }

    @Test
    @DisplayName("Deve lançar exceção para valor não numérico")
    void deveLancarExcecaoParaValorNaoNumerico() {
        FieldSet fieldSet = criarFieldSet(
                "xyz-000", "001111111-1", "DEBITO", "abc", "2026-03-10", "Teste");

        assertThrows(NumberFormatException.class, () -> mapper.mapFieldSet(fieldSet));
    }
}

