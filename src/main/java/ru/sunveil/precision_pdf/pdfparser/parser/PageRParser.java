package ru.sunveil.precision_pdf.pdfparser.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ru.sunveil.precision_pdf.pdfparser.config.ExtractionConfig;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class PageRParser extends AbstractRegionsJsonSegmentsParser {

    private static final String PAGER_OUTPUTS_DIR = "pageroutputs";
    private final ObjectMapper curlMapper = new ObjectMapper();

    @Override
    protected Path produceSegmentationJson(File pdfFile, ExtractionConfig config)
            throws IOException, InterruptedException {
        return executePageRCurlAndSaveJson(pdfFile);
    }

    @Override
    protected String parseFailureMessage() {
        return "Failed to parse PDF with PageR segment JSON";
    }

    private Path executePageRCurlAndSaveJson(File pdfFile) throws IOException, InterruptedException {
        Path outputsDir = Path.of(PAGER_OUTPUTS_DIR).toAbsolutePath();
        if (!Files.exists(outputsDir)) {
            Files.createDirectories(outputsDir);
        }
        if (!Files.isDirectory(outputsDir)) {
            throw new IOException("Path is not a directory: " + outputsDir);
        }

        String curlExecutable = resolveCurlExecutable();
        ProcessBuilder processBuilder = new ProcessBuilder(
                curlExecutable,
                "--silent",
                "--show-error",
                "-X", "POST",
                "http://127.0.0.1:8000/",
                "-F", "file=@" + pdfFile.getAbsolutePath(),
                "-F", "process={\"glam_rows\": true}"
        );
        Process process = processBuilder.start();

        String responseBody;
        String errorBody;
        try (var inputStream = process.getInputStream();
             var errorStream = process.getErrorStream()) {
            responseBody = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8).trim();
            errorBody = new String(errorStream.readAllBytes(), StandardCharsets.UTF_8).trim();
        }

        int exitCode = process.waitFor();
        if (exitCode != 0 || responseBody.isBlank()) {
            throw new IOException(curlExecutable + " failed for PageR segmentation. Exit code: " + exitCode +
                    (errorBody.isBlank() ? "" : ", stderr: " + errorBody));
        }

        JsonNode jsonNode;
        try {
            jsonNode = curlMapper.readTree(responseBody);
        } catch (Exception e) {
            String responsePrefix = responseBody.length() > 300 ? responseBody.substring(0, 300) + "..." : responseBody;
            throw new IOException("PageR service returned non-JSON response: " + responsePrefix, e);
        }
        Path responsePath = outputsDir.resolve("response_" + System.currentTimeMillis() + ".json");
        curlMapper.writerWithDefaultPrettyPrinter().writeValue(responsePath.toFile(), jsonNode);
        return responsePath;
    }

    private static String resolveCurlExecutable() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        return osName.contains("win") ? "curl.exe" : "curl";
    }
}
