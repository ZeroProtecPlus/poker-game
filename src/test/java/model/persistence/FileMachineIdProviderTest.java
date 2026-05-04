package model.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FileMachineIdProvider lifecycle: generation, caching,
 * corruption handling, directory creation, and thread safety.
 */
class FileMachineIdProviderTest {

    private static final Pattern UUID_V4 = Pattern.compile(
        "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    @TempDir
    Path tempDir;

    // =========================================================================
    // RED: Tests written first — FileMachineIdProvider does not exist yet
    // =========================================================================

    @Test
    void shouldGenerateValidUuidOnFirstCall() throws Exception {
        Path testDir = tempDir.resolve(".pokergame");
        FileMachineIdProvider provider = new FileMachineIdProvider(testDir);

        String machineId = provider.getMachineId();

        assertNotNull(machineId);
        assertTrue(UUID_V4.matcher(machineId).matches(),
            "machineId should be a valid UUID v4, got: " + machineId);
        assertTrue(Files.exists(testDir.resolve("machine.id")),
            "machine.id file should be created");
    }

    @Test
    void shouldReturnSameCachedValueOnSubsequentCalls() throws Exception {
        Path testDir = tempDir.resolve(".pokergame");
        FileMachineIdProvider provider = new FileMachineIdProvider(testDir);

        String first = provider.getMachineId();
        String second = provider.getMachineId();

        assertSame(first, second, "should return the same cached instance");
    }

    @Test
    void shouldReadExistingValidUuid() throws Exception {
        Path testDir = tempDir.resolve(".pokergame");
        Files.createDirectories(testDir);
        String knownUuid = UUID.randomUUID().toString();
        Files.writeString(testDir.resolve("machine.id"), knownUuid);

        FileMachineIdProvider provider = new FileMachineIdProvider(testDir);
        String machineId = provider.getMachineId();

        assertEquals(knownUuid, machineId,
            "should read existing valid UUID from file");
    }

    @Test
    void shouldRegenerateUuidOnCorruptedFile() throws Exception {
        Path testDir = tempDir.resolve(".pokergame");
        Files.createDirectories(testDir);
        Files.writeString(testDir.resolve("machine.id"), "corrupted-garbage-data");

        FileMachineIdProvider provider = new FileMachineIdProvider(testDir);
        String machineId = provider.getMachineId();

        assertTrue(UUID_V4.matcher(machineId).matches(),
            "should generate new valid UUID when file is corrupted");
        String fileContent = Files.readString(testDir.resolve("machine.id")).trim();
        assertEquals(machineId, fileContent,
            "corrupted file should be overwritten with new UUID");
    }

    @Test
    void shouldCreateMissingDirectory() throws Exception {
        Path testDir = tempDir.resolve("nonexistent").resolve(".pokergame");
        assertFalse(Files.exists(testDir), "directory should not exist yet");

        FileMachineIdProvider provider = new FileMachineIdProvider(testDir);
        String machineId = provider.getMachineId();

        assertTrue(Files.exists(testDir), "directory should be created");
        assertTrue(UUID_V4.matcher(machineId).matches(),
            "should generate valid UUID even when dir is missing");
    }

    @Test
    void shouldReturnSameIdAcrossConcurrentCalls() throws Exception {
        Path testDir = tempDir.resolve(".pokergame");
        FileMachineIdProvider provider = new FileMachineIdProvider(testDir);

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                latch.await();
                return provider.getMachineId();
            }));
        }

        latch.countDown();
        executor.shutdown();

        String expected = futures.get(0).get();
        for (Future<String> f : futures) {
            assertEquals(expected, f.get(),
                "all threads should receive the same machine ID");
        }
    }

    @Test
    void shouldHandleEmptyFileAsCorrupted() throws Exception {
        Path testDir = tempDir.resolve(".pokergame");
        Files.createDirectories(testDir);
        Files.writeString(testDir.resolve("machine.id"), "");

        FileMachineIdProvider provider = new FileMachineIdProvider(testDir);
        String machineId = provider.getMachineId();

        assertTrue(UUID_V4.matcher(machineId).matches(),
            "should regenerate UUID when file is empty");
    }

    @Test
    void shouldImplementsMachineIdProviderInterface() throws Exception {
        Path testDir = tempDir.resolve(".pokergame");
        FileMachineIdProvider provider = new FileMachineIdProvider(testDir);

        assertInstanceOf(MachineIdProvider.class, provider,
            "FileMachineIdProvider should implement MachineIdProvider");
    }
}
