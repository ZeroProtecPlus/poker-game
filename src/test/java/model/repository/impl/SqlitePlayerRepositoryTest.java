package model.repository.impl;

import model.User;
import model.persistence.ConnectionFactory;
import model.persistence.DatabaseBootstrapper;
import model.repository.PlayerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for SqlitePlayerRepository.
 */
class SqlitePlayerRepositoryTest {

    private PlayerRepository repository;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws Exception {
        Path dbFile = tempDir.resolve("players.db");
        ConnectionFactory factory = new ConnectionFactory(dbFile.toString());
        DatabaseBootstrapper bootstrapper = new DatabaseBootstrapper(factory);
        bootstrapper.bootstrap();
        repository = new SqlitePlayerRepository(factory);
    }

    @Test
    void shouldSaveAndFindPlayerById() throws Exception {
        User player = new User("player-1", "Alice");
        player.setChips(5000);

        repository.save(player);
        Optional<User> found = repository.findById("player-1");

        assertTrue(found.isPresent(), "player should be found by id");
        assertEquals("Alice", found.get().getName());
        assertEquals(5000, found.get().getChips());
    }

    @Test
    void shouldFindPlayerByName() throws Exception {
        User player = new User("player-2", "Bob");
        repository.save(player);

        Optional<User> found = repository.findByName("Bob");

        assertTrue(found.isPresent(), "player should be found by name");
        assertEquals("player-2", found.get().getPlayerId());
    }

    @Test
    void shouldReturnEmptyWhenPlayerNotFound() throws Exception {
        Optional<User> found = repository.findById("non-existent");
        assertTrue(found.isEmpty(), "should return empty for unknown id");
    }

    @Test
    void shouldUpdateExistingPlayer() throws Exception {
        User player = new User("player-3", "Charlie");
        player.setChips(1000);
        repository.save(player);

        player.setChips(2000);
        repository.save(player);

        Optional<User> found = repository.findById("player-3");
        assertTrue(found.isPresent());
        assertEquals(2000, found.get().getChips(), "chips should be updated");
    }

    @Test
    void shouldDeletePlayer() throws Exception {
        User player = new User("player-4", "Diana");
        repository.save(player);

        boolean deleted = repository.delete("player-4");

        assertTrue(deleted, "delete should return true");
        assertTrue(repository.findById("player-4").isEmpty(), "player should no longer exist");
    }

    @Test
    void shouldReturnFalseWhenDeletingNonExistentPlayer() throws Exception {
        boolean deleted = repository.delete("no-one");
        assertFalse(deleted, "delete should return false for unknown player");
    }

    @Test
    void shouldEnforceUniqueNameConstraint() throws Exception {
        User alice1 = new User("p1", "Alice");
        User alice2 = new User("p2", "Alice");
        repository.save(alice1);

        assertThrows(Exception.class, () -> repository.save(alice2),
            "saving duplicate name should throw");
    }

    @Test
    void shouldFindAllPlayers() throws Exception {
        repository.save(new User("p10", "Eve"));
        repository.save(new User("p11", "Frank"));

        List<User> all = repository.findAll();

        assertEquals(2, all.size(), "should return all players");
    }
}
