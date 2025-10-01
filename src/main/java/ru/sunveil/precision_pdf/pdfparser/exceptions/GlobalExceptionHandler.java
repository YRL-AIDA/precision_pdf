package ru.sunveil.precision_pdf.pdfparser.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MultipartException;
import ru.sunveil.precision_pdf.controller.dto.ApiResponse;

import java.io.IOException;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PdfParseException.class)
    public ResponseEntity<ApiResponse<String>> handlePdfParseException(PdfParseException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("PDF parsing error: " + e.getMessage(), 0));
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<ApiResponse<String>> handleIOException(IOException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("IO error: " + e.getMessage(), 0));
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiResponse<String>> handleMultipartException(MultipartException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("File upload error: " + e.getMessage(), 0));
    }
}