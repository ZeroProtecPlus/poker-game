package model.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * File-based implementation of {@link MachineIdProvider}.
 * Persists a UUID v4 to {@code <dir>/machine.id}.
 * Uses lazy initialization with double-checked locking for thread safety.
 * <p>
 * Supports a custom directory path for testing (production uses {@code ~/.pokergame}).
 */
public class FileMachineIdProvider implements MachineIdProvider {

    private static final Pattern UUID_V4 = Pattern.compile(
        "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    /** Production singleton instance. */
    public static final FileMachineIdProvider INSTANCE = new FileMachineIdProvider();

    private volatile String machineId;
    private final Path dir;
    private final Path file;

    /**
     * Production constructor: uses {@code ~/.pokergame/machine.id}.
     */
    private FileMachineIdProvider() {
        this.dir = Path.of(System.getProperty("user.home"), ".pokergame");
        this.file = dir.resolve("machine.id");
    }

    /**
     * Test constructor: allows overriding the directory path.
     *
     * @param dir the directory to store machine.id in
     */
    FileMachineIdProvider(Path dir) {
        this.dir = dir;
        this.file = dir.resolve("machine.id");
    }

    @Override
    public String getMachineId() {
        if (machineId != null) {
            return machineId;
        }
        synchronized (this) {
            if (machineId != null) {
                return machineId;
            }
            // Lazy init: evita I/O hasta que realmente se necesita el ID.
            machineId = resolveMachineId();
            return machineId;
        }
    }

    private String resolveMachineId() {
        // Reusa ID previo si es válido; si no, genera uno nuevo persistente.
        String existing = readFromFile();
        if (existing != null && isValidUuid(existing)) {
            return existing;
        }
        String newId = generateUuid();
        writeToFile(newId);
        return newId;
    }

    String readFromFile() {
        if (!Files.exists(file)) {
            return null;
        }
        try {
            return Files.readString(file).trim();
        } catch (IOException e) {
            System.err.println("Warning: failed to read machine.id: " + e.getMessage());
            return null;
        }
    }

    void writeToFile(String uuid) {
        try {
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
            // Escribe una sola vez para asegurar estabilidad entre reinicios.
            Files.writeString(file, uuid);
        } catch (IOException e) {
            System.err.println("Warning: failed to write machine.id: " + e.getMessage());
        }
    }

    String generateUuid() {
        return UUID.randomUUID().toString();
    }

    boolean isValidUuid(String s) {
        return s != null && UUID_V4.matcher(s).matches();
    }
}
