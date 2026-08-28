package audio;

import view.fx.JavaFxBootstrap;

/**
 * TDD tests for {@link BackgroundMusicPlayer} — singleton, track list, and basic lifecycle.
 *
 * <p>Audio cross-fade behaviour requires manual verification because MediaPlayer
 * internals are not observable from unit tests. The player lifecycle (start/stop)
 * is exercised on the FX thread via {@link JavaFxBootstrap#ensureStarted()}.
 *
 * <p>Run after {@code mvn test-compile dependency:build-classpath -Dmdep.outputFile=target/test-cp.txt}:
 * <pre>{@code java -cp "target/test-classes;target/classes;<deps>" audio.BackgroundMusicPlayerTest}</pre>
 */
public class BackgroundMusicPlayerTest {

    private static volatile boolean javafxInitialized;

    public static void main(String[] args) throws Exception {
        ensureJavaFxInitialized();

        shouldBeSingleton();
        shouldHaveCorrectTrackList();
        shouldStartAndStopWithoutException();
        shouldPlayAndStopWithinTimeout();
        shouldReturnToFirstTrackAfterFullCycle();

        System.out.println("BackgroundMusicPlayerTest: all tests passed");
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Singleton
    // ═════════════════════════════════════════════════════════════════════════

    private static void shouldBeSingleton() {
        var a = BackgroundMusicPlayer.getInstance();
        var b = BackgroundMusicPlayer.getInstance();
        require(a == b, "getInstance() must return same object; a=" + a + " b=" + b);
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Track list
    // ═════════════════════════════════════════════════════════════════════════

    private static void shouldHaveCorrectTrackList() {
        var player = BackgroundMusicPlayer.getInstance();
        String[] tracks = player.getTrackPaths();
        require(tracks.length == 3, "Expected 3 tracks, got " + tracks.length);
        require(tracks[0].contains("Soviet Casino Noir"),
            "Track 0 should be Soviet Casino Noir, got: " + tracks[0]);
        require(tracks[1].contains("The_House_Does_Not_Gamble"),
            "Track 1 should be The_House_Does_Not_Gamble, got: " + tracks[1]);
        require(tracks[2].contains("The_Lucky_General"),
            "Track 2 should be The_Lucky_General, got: " + tracks[2]);
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Lifecycle: start + stop (no exceptions)
    // ═════════════════════════════════════════════════════════════════════════

    private static void shouldStartAndStopWithoutException() {
        var player = BackgroundMusicPlayer.getInstance();
        try {
            player.start();
            // Give FX thread a moment to begin playback
            Thread.sleep(500);

            require(player.getCurrentPlayer() != null,
                "currentPlayer should be non-null after start()");
            require(player.isPlaying(),
                "playing should be true after start()");

            player.stop();
            Thread.sleep(300);

            require(player.getCurrentPlayer() == null,
                "currentPlayer should be null after stop()");
            require(!player.isPlaying(),
                "playing should be false after stop()");
        } catch (Exception e) {
            throw new AssertionError("start/stop should not throw: " + e.getMessage(), e);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Playback smoke test (start → brief wait → stop)
    // ═════════════════════════════════════════════════════════════════════════

    private static void shouldPlayAndStopWithinTimeout() {
        var player = BackgroundMusicPlayer.getInstance();
        try {
            player.start();
            // Wait a short time to let audio pipeline initialise
            Thread.sleep(2000);

            require(player.isPlaying(),
                "player should still be playing after 2s");

            player.stop();
            Thread.sleep(300);
            require(!player.isPlaying(),
                "player should be stopped");
        } catch (Exception e) {
            throw new AssertionError("playback smoke test failed: " + e.getMessage(), e);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Cycle check: after stop(), next start begins from track 0
    // ═════════════════════════════════════════════════════════════════════════

    private static void shouldReturnToFirstTrackAfterFullCycle() {
        var player = BackgroundMusicPlayer.getInstance();
        // After previous test's stop(), a fresh start should reset to track 0
        player.start();
        try {
            Thread.sleep(800);
            require(player.isPlaying(), "should be playing after fresh start");
            int firstIndex = getCurrentTrackIndex(player);
            require(firstIndex == 0,
                "fresh start should begin at track 0, got: " + firstIndex);
        } catch (Exception e) {
            throw new AssertionError("cycle reset failed: " + e.getMessage(), e);
        } finally {
            player.stop();
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Helpers
    // ═════════════════════════════════════════════════════════════════════════

    private static void ensureJavaFxInitialized() {
        if (javafxInitialized) return;
        synchronized (BackgroundMusicPlayerTest.class) {
            if (javafxInitialized) return;
            JavaFxBootstrap.ensureStarted();
            javafxInitialized = true;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T getField(Object obj, String fieldName) {
        try {
            java.lang.reflect.Field f;
            Class<?> clazz = obj.getClass();
            while (clazz != null) {
                try {
                    f = clazz.getDeclaredField(fieldName);
                    f.setAccessible(true);
                    return (T) f.get(obj);
                } catch (NoSuchFieldException e) {
                    clazz = clazz.getSuperclass();
                }
            }
            throw new AssertionError(
                "Field '" + fieldName + "' not found in " + obj.getClass().getName());
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(
                "Cannot access field '" + fieldName + "': " + ex.getMessage(), ex);
        }
    }

    private static int getCurrentTrackIndex(BackgroundMusicPlayer player) {
        return getField(player, "currentTrackIndex");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
