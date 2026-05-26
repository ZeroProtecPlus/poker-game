package config;

import network.protocol.LanConstants;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Persisted user preferences (host port, last client host, etc.).
 */
public final class GameSettings {

    private static final String DIR = ".pokergame";
    private static final String FILE = "settings.properties";
    private static final String KEY_HOST_PORT = "host.port";
    private static final String KEY_CLIENT_HOST = "client.host";
    private static final String KEY_UI_SYNC_TIMEOUT_MS = "ui.syncTimeoutMs";
    private static final String KEY_MUSIC_VOLUME = "music.volume";

    private static volatile GameSettings instance;

    private int hostPort = LanConstants.DEFAULT_PORT;
    private String clientHost = "127.0.0.1";
    private long uiSyncTimeoutMs = 60_000L;
    private double musicVolume = 0.5;

    private GameSettings() {
        load();
    }

    public static GameSettings get() {
        if (instance == null) {
            synchronized (GameSettings.class) {
                if (instance == null) {
                    instance = new GameSettings();
                }
            }
        }
        return instance;
    }

    public int getHostPort() {
        return hostPort;
    }

    public void setHostPort(int hostPort) {
        this.hostPort = hostPort;
    }

    public String getClientHost() {
        return clientHost;
    }

    public void setClientHost(String clientHost) {
        this.clientHost = clientHost != null ? clientHost.trim() : "127.0.0.1";
    }

    public long getUiSyncTimeoutMs() {
        return uiSyncTimeoutMs;
    }

    public void setUiSyncTimeoutMs(long uiSyncTimeoutMs) {
        this.uiSyncTimeoutMs = Math.max(3_000L, uiSyncTimeoutMs);
    }

    public double getMusicVolume() {
        return musicVolume;
    }

    public void setMusicVolume(double musicVolume) {
        this.musicVolume = Math.max(0.0, Math.min(1.0, musicVolume));
    }

    public void load() {
        Path path = settingsPath();
        if (!Files.isRegularFile(path)) {
            return;
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            props.load(in);
            hostPort = parsePort(props.getProperty(KEY_HOST_PORT), hostPort);
            clientHost = props.getProperty(KEY_CLIENT_HOST, clientHost);
            uiSyncTimeoutMs = parseLong(
                props.getProperty(KEY_UI_SYNC_TIMEOUT_MS),
                uiSyncTimeoutMs
            );
            musicVolume = parseDouble(
                props.getProperty(KEY_MUSIC_VOLUME),
                musicVolume
            );
        } catch (IOException ex) {
            System.err.println("Warning: could not load settings: " + ex.getMessage());
        }
    }

    public void save() {
        Path path = settingsPath();
        try {
            Files.createDirectories(path.getParent());
            Properties props = new Properties();
            props.setProperty(KEY_HOST_PORT, String.valueOf(hostPort));
            props.setProperty(KEY_CLIENT_HOST, clientHost);
            props.setProperty(KEY_UI_SYNC_TIMEOUT_MS, String.valueOf(uiSyncTimeoutMs));
            props.setProperty(KEY_MUSIC_VOLUME, String.valueOf(musicVolume));
            try (OutputStream out = Files.newOutputStream(path)) {
                props.store(out, "Poker Game settings");
            }
        } catch (IOException ex) {
            System.err.println("Warning: could not save settings: " + ex.getMessage());
        }
    }

    private static Path settingsPath() {
        return Paths.get(System.getProperty("user.home"), DIR, FILE);
    }

    private static int parsePort(String raw, int fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            int port = Integer.parseInt(raw.trim());
            return port > 0 && port <= 65535 ? port : fallback;
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static double parseDouble(String raw, double fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            double val = Double.parseDouble(raw.trim());
            return val >= 0.0 && val <= 1.0 ? val : fallback;
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static long parseLong(String raw, long fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
