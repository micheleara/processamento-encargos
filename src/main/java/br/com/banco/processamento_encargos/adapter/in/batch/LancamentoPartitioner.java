package br.com.banco.processamento_encargos.adapter.in.batch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.item.ExecutionContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class LancamentoPartitioner implements Partitioner {

    private final Path filePath;
    private final int gridSize;

    public LancamentoPartitioner(Path filePath, int gridSize) {
        this.filePath = filePath;
        this.gridSize = gridSize;
    }

    @Override
    public Map<String, ExecutionContext> partition(int gridSize) {
        int effectiveGridSize = this.gridSize > 0 ? this.gridSize : gridSize;
        int totalLines = countLines();
        int linesPerPartition = Math.max(1, totalLines / effectiveGridSize);

        log.info("Particionando arquivo: totalLinhas={} partições={} linhasPorPartição={}",
                totalLines, effectiveGridSize, linesPerPartition);

        Map<String, ExecutionContext> partitions = new HashMap<>();
        for (int i = 0; i < effectiveGridSize; i++) {
            ExecutionContext context = new ExecutionContext();
            int startLine = i * linesPerPartition;
            int maxItems = (i == effectiveGridSize - 1) ? (totalLines - startLine) : linesPerPartition;

            if (maxItems <= 0) break;

            context.putInt("startLine", startLine);
            context.putInt("maxItems", maxItems);
            context.putString("filePath", filePath.toAbsolutePath().toString());

            String partitionName = "partition" + i;
            partitions.put(partitionName, context);
            log.debug("Partição {}: startLine={} maxItems={}", partitionName, startLine, maxItems);
        }

        return partitions;
    }

    private int countLines() {
        try (var lines = Files.lines(filePath)) {
            // Subtrai 1 para desconsiderar o cabeçalho
            return (int) Math.max(0, lines.count() - 1);
        } catch (IOException e) {
            log.error("Erro ao contar linhas do arquivo: {}", filePath, e);
            throw new RuntimeException("Não foi possível contar as linhas do arquivo CSV", e);
        }
    }
}

