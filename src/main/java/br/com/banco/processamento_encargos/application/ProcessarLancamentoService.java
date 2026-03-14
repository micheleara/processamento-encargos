package br.com.banco.processamento_encargos.application;

import br.com.banco.processamento_encargos.domain.model.ContaInfo;
import br.com.banco.processamento_encargos.domain.model.Lancamento;
import br.com.banco.processamento_encargos.domain.model.ResultadoProcessamento;
import br.com.banco.processamento_encargos.domain.port.in.ProcessarLancamentoPort;
import br.com.banco.processamento_encargos.domain.port.out.AtualizarSaldoContaPort;
import br.com.banco.processamento_encargos.domain.port.out.ConsultarClienteContaPort;
import br.com.banco.processamento_encargos.domain.service.ValidacaoLancamentoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessarLancamentoService implements ProcessarLancamentoPort {

    private final ConsultarClienteContaPort consultarClienteContaPort;
    private final AtualizarSaldoContaPort atualizarSaldoContaPort;
    private final ValidacaoLancamentoService validacaoService;

    @Override
    public ResultadoProcessamento processar(Lancamento lancamento) {
        log.debug("Processando lançamento id={} conta={}", lancamento.idLancamento(), lancamento.numeroConta());

        // 1. Consultar dados da conta via Sistema de Contas
        ContaInfo contaInfo = consultarClienteContaPort.consultarCliente(
                lancamento.numeroConta(), lancamento.idLancamento());

        // 2. Validar regras de negócio
        Optional<String> motivoRejeicao = validacaoService.validar(lancamento, contaInfo);

        // 3. Se inválido → REJEITADO
        if (motivoRejeicao.isPresent()) {
            log.info("Lançamento REJEITADO id={} motivo={}", lancamento.idLancamento(), motivoRejeicao.get());
            return ResultadoProcessamento.rejeitado(lancamento, motivoRejeicao.get());
        }

        // 4. Se válido → notificar atualização de saldo e retornar PROCESSADO
        atualizarSaldoContaPort.publicarAtualizacaoSaldo(
                lancamento.numeroConta(), lancamento.tipoLancamento(), lancamento.valor());

        log.info("Lançamento PROCESSADO id={}", lancamento.idLancamento());
        return ResultadoProcessamento.processado(lancamento);
    }
}

