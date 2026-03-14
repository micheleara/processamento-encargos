package br.com.banco.processamento_encargos.adapter.in.batch;

import br.com.banco.processamento_encargos.domain.model.Lancamento;
import br.com.banco.processamento_encargos.domain.model.TipoLancamento;
import org.springframework.batch.item.file.mapping.FieldSetMapper;
import org.springframework.batch.item.file.transform.FieldSet;
import org.springframework.validation.BindException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class LancamentoCsvFieldSetMapper implements FieldSetMapper<Lancamento> {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Override
    public Lancamento mapFieldSet(FieldSet fieldSet) throws BindException {
        return new Lancamento(
                fieldSet.readString("idLancamento"),
                fieldSet.readString("numeroConta"),
                TipoLancamento.valueOf(fieldSet.readString("tipoLancamento")),
                new BigDecimal(fieldSet.readString("valor")),
                LocalDate.parse(fieldSet.readString("dataLancamento"), DATE_FORMATTER),
                fieldSet.readString("descricao")
        );
    }
}

