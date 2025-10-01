package ru.sunveil.precision_pdf.controller.dto;

import lombok.Data;

@Data
public class UploadResponse {
    private String filename;
    private long size;
    private String contentType;

    public UploadResponse(String filename, long size, String contentType) {
        this.filename = filename;
        this.size = size;
        this.contentType = contentType;
    }
}
