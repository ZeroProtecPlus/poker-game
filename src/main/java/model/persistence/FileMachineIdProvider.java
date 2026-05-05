package model.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Implementación basada en archivo de {@link MachineIdProvider}.
 * Persiste un UUID v4 en {@code <dir>/machine.id}.
 * Usa inicialización lazy con doble chequeo para ser thread-safe.
 * <p>
 * Permite path custom para tests (en prod usa {@code ~/.pokergame}).
 */
public class FileMachineIdProvider implements MachineIdProvider {

    private static final Pattern UUID_V4 = Pattern.compile(
        "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    /** Singleton de producción. */
    public static final FileMachineIdProvider INSTANCE = new FileMachineIdProvider();

    private volatile String machineId;
    private final Path dir;
    private final Path file;

    /**
     * Constructor de producción: usa {@code ~/.pokergame/machine.id}.
     */
    private FileMachineIdProvider() {
        this.dir = Path.of(System.getProperty("user.home"), ".pokergame");
        this.file = dir.resolve("machine.id");
    }

    /**
     * Constructor de testing: permite sobreescribir el directorio.
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
