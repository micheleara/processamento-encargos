package br.com.banco.processamento_encargos.core.usecase;

import br.com.banco.processamento_encargos.core.domain.model.ContaInfo;
import br.com.banco.processamento_encargos.core.domain.model.Lancamento;
import br.com.banco.processamento_encargos.core.domain.model.ResultadoProcessamento;
import br.com.banco.processamento_encargos.core.domain.model.TipoLancamento;
import br.com.banco.processamento_encargos.port.input.ProcessarLancamentoInputPort;
import br.com.banco.processamento_encargos.port.output.AtualizarSaldoContaOutputPort;
import br.com.banco.processamento_encargos.port.output.ConsultarClienteContaOutputPort;
import br.com.banco.processamento_encargos.port.output.PublicarLancamentoContabilOutputPort;

import java.math.BigDecimal;
import java.util.Optional;

public class ProcessarLancamentoUseCase implements ProcessarLancamentoInputPort {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ProcessarLancamentoUseCase.class);

    private final ConsultarClienteContaOutputPort consultarClienteContaPort;
    private final AtualizarSaldoContaOutputPort atualizarSaldoContaPort;
    private final PublicarLancamentoContabilOutputPort publicarLancamentoContabilPort;
    private final ValidacaoLancamentoUseCase validacaoService;

    public ProcessarLancamentoUseCase(
            ConsultarClienteContaOutputPort consultarClienteContaPort,
            AtualizarSaldoContaOutputPort atualizarSaldoContaPort,
            PublicarLancamentoContabilOutputPort publicarLancamentoContabilPort,
            ValidacaoLancamentoUseCase validacaoService) {
        this.consultarClienteContaPort = consultarClienteContaPort;
        this.atualizarSaldoContaPort = atualizarSaldoContaPort;
        this.publicarLancamentoContabilPort = publicarLancamentoContabilPort;
        this.validacaoService = validacaoService;
    }

    @Override
    public ResultadoProcessamento processar(Lancamento lancamento) {
        log.debug("Processando lançamento id={} conta={}", lancamento.idLancamento(), lancamento.numeroConta());

        // 1. Consultar dados da conta via Sistema de Contas
        ContaInfo contaInfo = consultarClienteContaPort.consultarCliente(
                lancamento.numeroConta(), lancamento.idLancamento());

        // 2. Validar regras de negócio
        Optional<String> motivoRejeicao = validacaoService.validar(lancamento, contaInfo);

        // 3. Se inválido → REJEITADO — retorna sem persistir (persistência delegada ao writer do batch)
        if (motivoRejeicao.isPresent()) {
            log.info("Lançamento RECUSADO id={} motivo={}", lancamento.idLancamento(), motivoRejeicao.get());
            return ResultadoProcessamento.rejeitado(lancamento, motivoRejeicao.get());
        }

        // 4. Calcular saldos
        BigDecimal saldoAnterior = contaInfo.saldo();
        BigDecimal saldoPosterior = lancamento.tipoLancamento() == TipoLancamento.DEBITO
                ? saldoAnterior.subtract(lancamento.valor())
                : saldoAnterior.add(lancamento.valor());

        // 5. Montar resultado PROCESSADO com saldos (persistência delegada ao writer do batch)
        ResultadoProcessamento resultado = ResultadoProcessamento.processado(lancamento, saldoAnterior, saldoPosterior);

        // 6. Registrar atualização de saldo pendente no mapa (será publicada após confirmação contábil — evento ⑦)
        // DEVE ocorrer antes do evento ④ para evitar race condition caso a confirmação ⑥ chegue muito rápido
        atualizarSaldoContaPort.publicarAtualizacaoSaldo(
                lancamento.idLancamento(), lancamento.numeroConta(), lancamento.tipoLancamento(), lancamento.valor());

        // 7. Publicar lançamento contábil (evento ④) — dispara a cadeia: gestao-contabil → confirmação ⑥ → saldo ⑦
        publicarLancamentoContabilPort.publicar(resultado);

        log.info("Lançamento PROCESSADO id={} saldo_anterior={} saldo_posterior={}",
                lancamento.idLancamento(), saldoAnterior, saldoPosterior);
        return resultado;
    }
}