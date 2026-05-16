package ru.sunveil.precision_pdf.pdfparser.parser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.sunveil.precision_pdf.pdfparser.config.ExtractionConfig;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public class DeepSeekOcrParser extends AbstractRegionsJsonSegmentsParser {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeepSeekOcrParser.class);

    @Override
    protected Path produceSegmentationJson(File pdfFile, ExtractionConfig config) throws IOException, InterruptedException {
        Path outputDir = Files.createTempDirectory("deepseek_ocr_parser_");
        Path scriptPath = Path.of("deepseek_ocr_parser", "main.py").toAbsolutePath();
        Path dotVenvUnixPython = Path.of("deepseek_ocr_parser", ".venv", "bin", "python").toAbsolutePath();
        Path dotVenvWindowsPython = Path.of("deepseek_ocr_parser", ".venv", "Scripts", "python.exe").toAbsolutePath();
        Path venvUnixPython = Path.of("deepseek_ocr_parser", "venv", "bin", "python").toAbsolutePath();
        Path venvWindowsPython = Path.of("deepseek_ocr_parser", "venv", "Scripts", "python.exe").toAbsolutePath();
        String pythonExecutable;
        if (Files.exists(dotVenvUnixPython)) {
            pythonExecutable = dotVenvUnixPython.toString();
        } else if (Files.exists(dotVenvWindowsPython)) {
            pythonExecutable = dotVenvWindowsPython.toString();
        } else if (Files.exists(venvUnixPython)) {
            pythonExecutable = venvUnixPython.toString();
        } else if (Files.exists(venvWindowsPython)) {
            pythonExecutable = venvWindowsPython.toString();
        } else {
            pythonExecutable = "python";
        }

        String mode = normalizeDeepseekMode(config);
        int dpi = config != null ? Math.max(72, config.getDeepseekOcrRenderDpi()) : 144;

        ProcessBuilder processBuilder = new ProcessBuilder(
                pythonExecutable,
                scriptPath.toString(),
                "--pdf",
                pdfFile.getAbsolutePath(),
                "--output-dir",
                outputDir.toAbsolutePath().toString(),
                "--mode",
                mode,
                "--render-dpi",
                Integer.toString(dpi)
        );
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        /*
         * Do not use readAllBytes() here: it blocks until the child exits, so Python stderr
         * (_log) never reaches the JVM until the process finishes — and the pipe can fill,
         * stalling the child. Drain lines in a side thread and log at DEBUG.
         */
        StringBuilder captured = new StringBuilder();
        Thread drainer = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    LOGGER.debug("[deepseek_ocr_parser] {}", line);
                    synchronized (captured) {
                        if (captured.length() < 200_000) {
                            captured.append(line).append(System.lineSeparator());
                        }
                    }
                }
            } catch (IOException e) {
                LOGGER.warn("Failed reading DeepSeek OCR subprocess output", e);
            }
        }, "deepseek-ocr-subprocess-log");
        drainer.start();
        int exitCode = process.waitFor();
        drainer.join();
        String outputText = captured.toString();
        if (exitCode != 0) {
            throw new IOException("DeepSeek OCR python failed with code " + exitCode + ". Output: " + outputText);
        }

        Path jsonPath = outputDir.resolve("segmentation.json");
        if (!Files.exists(jsonPath)) {
            throw new IOException("DeepSeek OCR JSON not found: " + jsonPath + ". Output: " + outputText);
        }
        return jsonPath;
    }

    @Override
    protected String parseFailureMessage() {
        return "Failed to parse PDF with DeepSeek OCR segment JSON";
    }

    private static String normalizeDeepseekMode(ExtractionConfig config) {
        if (config == null || config.getDeepseekOcrMode() == null || config.getDeepseekOcrMode().isBlank()) {
            return "auto";
        }
        String m = config.getDeepseekOcrMode().trim().toLowerCase(Locale.ROOT).replace('_', '-');
        if ("tf".equals(m) || "model".equals(m) || "gpu".equals(m)) {
            return "transformers";
        }
        if ("pymupdf".equals(m) || "pdf".equals(m) || "blocks".equals(m)) {
            return "stub";
        }
        if ("vllm".equals(m)) {
            return "transformers";
        }
        if ("transformers".equals(m) || "stub".equals(m) || "auto".equals(m)) {
            return m;
        }
        return "auto";
    }
}
