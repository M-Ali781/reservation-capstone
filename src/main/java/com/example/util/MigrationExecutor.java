package com.example.util;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.util.logging.Logger;

/** Executant JDBC pour les scripts H2 du TP (DDL simples, sans procedures). */
public class MigrationExecutor {
    private static final Logger LOG = Logger.getLogger(MigrationExecutor.class.getName());
    private final String url, username, password;
    public MigrationExecutor(String url, String username, String password) {
        this.url = url; this.username = username; this.password = password;
    }
    public void migrate() throws Exception {
        executeMigration("db/migration/v1_0_initial.sql");
        executeMigration("db/migration/v2_0_add_description.sql");
    }
    public void executeMigration(String path) throws Exception {
        byte[] bytes;
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
            if (input == null) throw new FileNotFoundException("Migration introuvable : " + path);
            bytes = input.readAllBytes();
        }
        StringBuilder checksum = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes)) checksum.append(String.format("%02x", b));
        try (Connection conn = DriverManager.getConnection(url, username, password)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS schema_history (script VARCHAR(255) PRIMARY KEY, checksum VARCHAR(64) NOT NULL, applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            }
            try (PreparedStatement stmt = conn.prepareStatement("SELECT checksum FROM schema_history WHERE script=?")) {
                stmt.setString(1, path);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        if (!checksum.toString().equals(rs.getString(1))) throw new IllegalStateException("Migration deja appliquee mais modifiee : " + path);
                        LOG.info("Migration deja appliquee : " + path);
                        return;
                    }
                }
            }
            // H2 valide implicitement les DDL. Chaque instruction est donc idempotente.
            String sql = new String(bytes, StandardCharsets.UTF_8).replaceAll("(?m)^\\s*--.*$", "");
            try (Statement stmt = conn.createStatement()) {
                for (String instruction : sql.split(";")) if (!instruction.trim().isEmpty()) stmt.execute(instruction.trim());
            }
            try (PreparedStatement stmt = conn.prepareStatement("INSERT INTO schema_history(script, checksum) VALUES (?, ?)")) {
                stmt.setString(1, path); stmt.setString(2, checksum.toString()); stmt.executeUpdate();
            }
            LOG.info("Migration appliquee : " + path);
        } catch (Exception e) { LOG.severe("Echec migration " + path + " : " + e.getMessage()); throw e; }
    }
}
