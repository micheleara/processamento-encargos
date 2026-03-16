package br.com.banco.processamento_encargos.application;

import br.com.banco.processamento_encargos.domain.model.ContaInfo;
import br.com.banco.processamento_encargos.domain.model.Lancamento;
import br.com.banco.processamento_encargos.domain.model.ResultadoProcessamento;
import br.com.banco.processamento_encargos.domain.model.TipoLancamento;
import br.com.banco.processamento_encargos.domain.port.in.ProcessarLancamentoPort;
import br.com.banco.processamento_encargos.domain.port.out.AtualizarSaldoContaPort;
import br.com.banco.processamento_encargos.domain.port.out.ConsultarClienteContaPort;
import br.com.banco.processamento_encargos.domain.port.out.SalvarResultadoProcessamentoPort;
import br.com.banco.processamento_encargos.domain.service.ValidacaoLancamentoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessarLancamentoService implements ProcessarLancamentoPort {

    private final ConsultarClienteContaPort consultarClienteContaPort;
    private final AtualizarSaldoContaPort atualizarSaldoContaPort;
    private final SalvarResultadoProcessamentoPort salvarResultadoPort;
    private final ValidacaoLancamentoService validacaoService;

    @Override
    public ResultadoProcessamento processar(Lancamento lancamento) {
        log.debug("Processando lançamento id={} conta={}", lancamento.idLancamento(), lancamento.numeroConta());

        // 1. Consultar dados da conta via Sistema de Contas
        ContaInfo contaInfo = consultarClienteContaPort.consultarCliente(
                lancamento.numeroConta(), lancamento.idLancamento());

        // 2. Validar regras de negócio
        Optional<String> motivoRejeicao = validacaoService.validar(lancamento, contaInfo);

        // 3. Se inválido → REJEITADO — persiste e retorna
        if (motivoRejeicao.isPresent()) {
            log.info("Lançamento REJEITADO id={} motivo={}", lancamento.idLancamento(), motivoRejeicao.get());
            ResultadoProcessamento resultado = ResultadoProcessamento.rejeitado(lancamento, motivoRejeicao.get());
            salvarResultadoPort.salvar(resultado);
            return resultado;
        }

        // 4. Calcular saldos
        BigDecimal saldoAnterior = contaInfo.saldo();
        BigDecimal saldoPosterior = lancamento.tipoLancamento() == TipoLancamento.DEBITO
                ? saldoAnterior.subtract(lancamento.valor())
                : saldoAnterior.add(lancamento.valor());

        // 5. Persistir resultado PROCESSADO com saldos
        ResultadoProcessamento resultado = ResultadoProcessamento.processado(lancamento, saldoAnterior, saldoPosterior);
        salvarResultadoPort.salvar(resultado);

        // 6. Registrar atualização de saldo pendente (publicada após confirmação contábil)
        atualizarSaldoContaPort.publicarAtualizacaoSaldo(
                lancamento.idLancamento(), lancamento.numeroConta(), lancamento.tipoLancamento(), lancamento.valor());

        log.info("Lançamento PROCESSADO id={} saldo_anterior={} saldo_posterior={}",
                lancamento.idLancamento(), saldoAnterior, saldoPosterior);
        return resultado;
    }
}

