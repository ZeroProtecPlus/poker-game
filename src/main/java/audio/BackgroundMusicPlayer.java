package audio;

import java.net.URL;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;

/**
 * Singleton background music player with cross-fade between tracks.
 *
 * <p>Plays 3 casino-noir MP3s in an infinite loop. Uses two {@link MediaPlayer}
 * instances so the next track can fade in while the current track fades out over
 * a configurable duration (3 seconds by default).
 *
 * <p>All {@link MediaPlayer} operations are marshalled onto the JavaFX
 * Application Thread via {@link Platform#runLater(Runnable)}.
 */
public final class BackgroundMusicPlayer {

    static final String[] TRACKS = {
        "/music/Soviet Casino Noir.mp3",
        "/music/The_House_Does_Not_Gamble.mp3",
        "/music/The_Lucky_General.mp3"
    };

    static final double CROSSFADE_SECONDS = 3.0;

    private static volatile BackgroundMusicPlayer instance;

    private MediaPlayer currentPlayer;
    private MediaPlayer nextPlayer;
    private int currentTrackIndex;
    private volatile boolean playing;
    private volatile double targetVolume = 1.0;
    private Timeline activeCrossFade;

    private BackgroundMusicPlayer() {}

    /**
     * Returns the process-wide singleton.
     */
    public static BackgroundMusicPlayer getInstance() {
        if (instance == null) {
            synchronized (BackgroundMusicPlayer.class) {
                if (instance == null) {
                    instance = new BackgroundMusicPlayer();
                }
            }
        }
        return instance;
    }

    /**
     * Begins playback from the first track. If already playing this is a no-op.
     *
     * <p>May be called from any thread; the real work is posted to the FX thread.
     */
    public void start() {
        Platform.runLater(() -> {
            if (playing) return;
            playing = true;
            currentTrackIndex = 0;
            playCurrentTrack();
        });
    }

    /**
     * Stops all playback immediately and disposes both players.
     *
     * <p>May be called from any thread; the real work is posted to the FX thread.
     */
    public void stop() {
        Platform.runLater(() -> {
            playing = false;
            cancelCrossFade();
            disposeSafely(currentPlayer);
            currentPlayer = null;
            disposeSafely(nextPlayer);
            nextPlayer = null;
        });
    }

    /**
     * Sets the playback volume (0.0 = silent, 1.0 = full).
     * Applies immediately to the current track. May be called from any thread.
     */
    public void setVolume(double level) {
        targetVolume = Math.max(0.0, Math.min(1.0, level));
        Platform.runLater(() -> {
            if (currentPlayer != null) {
                currentPlayer.setVolume(targetVolume);
            }
        });
    }

    /**
     * Returns the current target volume (0.0–1.0).
     */
    public double getVolume() {
        return targetVolume;
    }

    // ── package-private accessors (used by tests via reflection) ────────

    MediaPlayer getCurrentPlayer() {
        return currentPlayer;
    }

    boolean isPlaying() {
        return playing;
    }

    String[] getTrackPaths() {
        return TRACKS;
    }

    // ── internal playback logic (MUST run on FX thread) ─────────────────

    private void playCurrentTrack() {
        if (!playing) return;

        var url = resolveTrack(currentTrackIndex);
        if (url == null) {
            log("Cannot load track: " + TRACKS[currentTrackIndex]);
            advanceToNextTrack();
            return;
        }

        var media = new Media(url.toString());
        var player = new MediaPlayer(media);
        currentPlayer = player;
        player.setVolume(targetVolume);

        player.setOnReady(() -> scheduleCrossFade(media, player, currentTrackIndex));
        player.setOnError(() -> {
            log("Error playing: " + TRACKS[currentTrackIndex]);
            advanceToNextTrack();
        });
        player.play();
    }

    private void scheduleCrossFade(Media media, MediaPlayer player, int trackIndex) {
        if (!playing || player != currentPlayer) return;

        var totalDuration = media.getDuration();
        if (totalDuration == null
                || totalDuration.isUnknown()
                || totalDuration.lessThanOrEqualTo(Duration.seconds(CROSSFADE_SECONDS))) {
            // Track too short for cross-fade — wait for natural end
            player.setOnEndOfMedia(this::onCurrentTrackEnded);
            return;
        }

        var crossFadeStart = totalDuration.subtract(Duration.seconds(CROSSFADE_SECONDS));
        var crossFadeTriggered = new boolean[1];

        player.currentTimeProperty().addListener((obs, old, now) -> {
            if (crossFadeTriggered[0] || now == null) return;
            if (now.greaterThanOrEqualTo(crossFadeStart)) {
                crossFadeTriggered[0] = true;
                beginCrossFade(trackIndex);
            }
        });

        // Safety net: if onEndOfMedia fires before the time listener triggers
        player.setOnEndOfMedia(() -> {
            if (!crossFadeTriggered[0]) {
                crossFadeTriggered[0] = true;
                beginCrossFade(trackIndex);
            }
        });
    }

    private void beginCrossFade(int currentIndex) {
        if (!playing) return;

        int nextIndex = (currentIndex + 1) % TRACKS.length;
        var url = resolveTrack(nextIndex);
        if (url == null) {
            log("Cannot load next track: " + TRACKS[nextIndex]);
            advanceToNextTrack();
            return;
        }

        var media = new Media(url.toString());
        var next = new MediaPlayer(media);
        next.setVolume(0.0);
        nextPlayer = next;

        next.setOnReady(() -> next.play());
        next.setOnError(() -> log("Error playing next: " + TRACKS[nextIndex]));

        cancelCrossFade();
        var current = currentPlayer;
        if (current == null) return;

        var timeline = new Timeline(
            new KeyFrame(Duration.ZERO,
                new KeyValue(current.volumeProperty(), current.getVolume()),
                new KeyValue(next.volumeProperty(), 0.0)),
            new KeyFrame(Duration.seconds(CROSSFADE_SECONDS),
                new KeyValue(current.volumeProperty(), 0.0),
                new KeyValue(next.volumeProperty(), targetVolume)));

        timeline.setOnFinished(e -> {
            activeCrossFade = null;
            disposeSafely(current);
            currentPlayer = next;
            nextPlayer = null;
            currentTrackIndex = nextIndex;

            if (playing) {
                scheduleCrossFade(next.getMedia(), next, nextIndex);
            }
        });

        activeCrossFade = timeline;
        timeline.play();
    }

    private void onCurrentTrackEnded() {
        if (!playing) return;
        advanceToNextTrack();
    }

    private void advanceToNextTrack() {
        disposeSafely(currentPlayer);
        currentPlayer = null;
        currentTrackIndex = (currentTrackIndex + 1) % TRACKS.length;
        if (playing) {
            playCurrentTrack();
        }
    }

    private URL resolveTrack(int index) {
        if (index < 0 || index >= TRACKS.length) return null;
        return getClass().getResource(TRACKS[index]);
    }

    private void cancelCrossFade() {
        if (activeCrossFade != null) {
            activeCrossFade.stop();
            activeCrossFade = null;
        }
    }

    private static void disposeSafely(MediaPlayer player) {
        if (player != null) {
            player.stop();
            player.dispose();
        }
    }

    private static void log(String msg) {
        System.err.println("[BackgroundMusic] " + msg);
    }
}
