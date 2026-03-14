package br.com.banco.processamento_encargos.adapter.in.batch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.item.ExecutionContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LancamentoPartitionerTest {

    @TempDir
    Path tempDir;

    private Path criarArquivoCsv(int linhasDados) throws IOException {
        Path arquivo = tempDir.resolve("lancamentos.csv");
        StringBuilder sb = new StringBuilder();
        sb.append("idLancamento,numeroConta,tipoLancamento,valor,dataLancamento,descricao\n");
        for (int i = 1; i <= linhasDados; i++) {
            sb.append("id-").append(i).append(",001234567-8,DEBITO,100.00,2026-03-10,Descricao ").append(i).append("\n");
        }
        Files.writeString(arquivo, sb.toString());
        return arquivo;
    }

    @Test
    @DisplayName("Deve criar partições proporcionais ao número de linhas")
    void deveCriarParticoesProporcionais() throws IOException {
        Path arquivo = criarArquivoCsv(100);
        LancamentoPartitioner partitioner = new LancamentoPartitioner(arquivo, 4);

        Map<String, ExecutionContext> partitions = partitioner.partition(4);

        assertEquals(4, partitions.size());
        for (ExecutionContext ctx : partitions.values()) {
            assertTrue(ctx.containsKey("startLine"));
            assertTrue(ctx.containsKey("maxItems"));
            assertTrue(ctx.containsKey("filePath"));
            assertTrue(ctx.getInt("maxItems") > 0);
        }
    }

    @Test
    @DisplayName("Deve distribuir todas as linhas sem perder nenhuma")
    void deveDistribuirTodasAsLinhas() throws IOException {
        int totalLinhas = 100;
        Path arquivo = criarArquivoCsv(totalLinhas);
        LancamentoPartitioner partitioner = new LancamentoPartitioner(arquivo, 4);

        Map<String, ExecutionContext> partitions = partitioner.partition(4);

        int somaItems = partitions.values().stream()
                .mapToInt(ctx -> ctx.getInt("maxItems"))
                .sum();
        assertEquals(totalLinhas, somaItems);
    }

    @Test
    @DisplayName("Deve usar gridSize do construtor quando maior que zero")
    void deveUsarGridSizeDoConstrutor() throws IOException {
        Path arquivo = criarArquivoCsv(50);
        LancamentoPartitioner partitioner = new LancamentoPartitioner(arquivo, 5);

        Map<String, ExecutionContext> partitions = partitioner.partition(10);

        assertEquals(5, partitions.size());
    }

    @Test
    @DisplayName("Deve parar de criar partições quando maxItems fica negativo")
    void deveParaQuandoNaoHaMaisLinhas() throws IOException {
        Path arquivo = criarArquivoCsv(2);
        LancamentoPartitioner partitioner = new LancamentoPartitioner(arquivo, 5);

        Map<String, ExecutionContext> partitions = partitioner.partition(5);

        assertFalse(partitions.isEmpty());
        assertTrue(partitions.size() <= 5);
        for (ExecutionContext ctx : partitions.values()) {
            assertTrue(ctx.getInt("maxItems") > 0);
        }
    }

    @Test
    @DisplayName("Deve armazenar caminho absoluto do arquivo em cada partição")
    void deveArmazenarCaminhoAbsolutoEmCadaParticao() throws IOException {
        Path arquivo = criarArquivoCsv(20);
        LancamentoPartitioner partitioner = new LancamentoPartitioner(arquivo, 2);

        Map<String, ExecutionContext> partitions = partitioner.partition(2);

        for (ExecutionContext ctx : partitions.values()) {
            assertEquals(arquivo.toAbsolutePath().toString(), ctx.getString("filePath"));
        }
    }

    @Test
    @DisplayName("Deve lançar exceção quando arquivo não existe")
    void deveLancarExcecaoQuandoArquivoNaoExiste() {
        Path arquivoInexistente = tempDir.resolve("inexistente.csv");
        LancamentoPartitioner partitioner = new LancamentoPartitioner(arquivoInexistente, 4);

        assertThrows(RuntimeException.class, () -> partitioner.partition(4));
    }
}

