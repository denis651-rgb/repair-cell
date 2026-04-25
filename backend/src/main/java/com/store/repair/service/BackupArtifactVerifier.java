package com.store.repair.service;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
public class BackupArtifactVerifier {

    private static final String SQLITE_HEADER = "SQLite format 3";

    public void verify(Path artifact, boolean zipped) {
        try {
            if (!Files.exists(artifact) || !Files.isRegularFile(artifact)) {
                throw new BusinessException("El archivo de backup no fue generado correctamente.");
            }

            long size = Files.size(artifact);
            if (size <= 0) {
                throw new BusinessException("El archivo de backup quedó vacío.");
            }

            if (zipped) {
                verifyZipArtifact(artifact);
            } else {
                verifySqliteArtifact(Files.newInputStream(artifact));
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new BusinessException("No se pudo verificar el archivo de backup generado.");
        }
    }

    private void verifyZipArtifact(Path artifact) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(artifact))) {
            ZipEntry entry = zis.getNextEntry();
            if (entry == null || !entry.getName().toLowerCase().endsWith(".db")) {
                throw new BusinessException("El archivo ZIP del backup no contiene una base de datos válida.");
            }

            verifySqliteArtifact(zis);
        }
    }

    private void verifySqliteArtifact(InputStream inputStream) throws IOException {
        byte[] header = inputStream.readNBytes(16);
        String headerText = new String(header, StandardCharsets.US_ASCII);
        if (!headerText.startsWith(SQLITE_HEADER)) {
            throw new BusinessException("El backup generado no tiene un formato SQLite válido.");
        }
    }
}
