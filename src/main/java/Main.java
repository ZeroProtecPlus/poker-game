import controller.GameController;
import controller.LanClientController;
import controller.LanHostController;
import config.GameSettings;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import view.LanDialogs;
import view.fx.JavaFxBootstrap;
import view.fx.MultiplayerMenuDialog;
import view.fx.SettingsDialog;
import view.fx.StartMenuDialog;

/**
 * Entry point. Default flow: start menu → mode selection → game.
 * Legacy CLI flags {@code --lan-host} / {@code --lan-client} skip the menu.
 */
public class Main {

    public static void main(String[] args) {
        if (args.length > 0 && "--lan-host".equals(args[0])) {
            Thread gameThread = runOnGameThread(() -> {
                try {
                    runLanHost(args);
                } catch (Exception ex) {
                    throw new IllegalStateException(ex.getMessage(), ex);
                }
            });
            try {
                gameThread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            Platform.exit();
            return;
        }
        if (args.length > 0 && "--lan-client".equals(args[0])) {
            Thread gameThread = runOnGameThread(() -> {
                try {
                    runLanClient();
                } catch (Exception ex) {
                    throw new IllegalStateException(ex.getMessage(), ex);
                }
            });
            try {
                gameThread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            Platform.exit();
            return;
        }

        JavaFxBootstrap.ensureStarted();
        // Defer music startup so the menu renders before large MP3 files
        // are loaded on the FX thread (avoids 5-min timeout).
        new Thread(() -> {
            try { Thread.sleep(1500); } catch (InterruptedException e) { return; }
            audio.BackgroundMusicPlayer.getInstance().start();
        }, "music-delayed-start").start();
        runMenuLoop();
        audio.BackgroundMusicPlayer.getInstance().stop();
        // Let the FX thread process the stop() before toolkit shutdown
        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        Platform.exit();
    }

    private static void runMenuLoop() {
        while (true) {
            StartMenuDialog.Choice choice = StartMenuDialog.showAndWaitBlocking();
            if (choice == null || choice == StartMenuDialog.Choice.CLOSED) {
                return;
            }
            switch (choice) {
                case SINGLE_PLAYER -> {
                    Thread gameThread = runOnGameThread(Main::runSinglePlayer);
                    try {
                        gameThread.join();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    // Continue loop — show StartMenu again instead of exiting.
                }
                case MULTIPLAYER -> runMultiplayerSubmenu();
                case SETTINGS -> SettingsDialog.showAndWaitBlocking();
                default -> { return; }
            }
        }
    }

    private static void runMultiplayerSubmenu() {
        while (true) {
            MultiplayerMenuDialog.Choice choice = MultiplayerMenuDialog.showAndWaitBlocking();
            if (choice == null || choice == MultiplayerMenuDialog.Choice.CLOSED) {
                return;
            }
            switch (choice) {
                case HOST -> {
                    Thread gameThread = runOnGameThread(() -> {
                        try {
                            runLanHost(new String[] { "--lan-host" });
                        } catch (Exception ex) {
                            throw new IllegalStateException(ex.getMessage(), ex);
                        }
                    });
                    try {
                        gameThread.join();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return;
                }
                case JOIN -> {
                    Thread gameThread = runOnGameThread(() -> {
                        try {
                            runLanClient();
                        } catch (Exception ex) {
                            throw new IllegalStateException(ex.getMessage(), ex);
                        }
                    });
                    try {
                        gameThread.join();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return;
                }
                case BACK -> {
                    return;
                }
                default -> { return; }
            }
        }
    }

    private static Thread runOnGameThread(Runnable action) {
        Thread gameThread = new Thread(() -> {
            try {
                action.run();
            } catch (IllegalStateException e) {
                String msg = e.getMessage();
                if (msg != null && msg.toLowerCase().contains("cancel")) {
                    // User-initiated cancellation — just log, no Alert needed
                    System.out.println("Cancelado: " + msg);
                } else {
                    try {
                        Platform.runLater(() -> {
                            Alert alert = new Alert(Alert.AlertType.WARNING);
                            alert.setTitle("Cancelado");
                            alert.setHeaderText(null);
                            alert.setContentText(msg);
                            alert.showAndWait();
                        });
                    } catch (IllegalStateException fxError) {
                        System.err.println("Cancelado: " + msg);
                    }
                }
            } catch (Exception e) {
                System.out.println("Ocurrió un error inesperado: " + e.getMessage());
            } finally {
                // Game thread finished naturally — cleanup happens in controllers.
                // No Platform.exit() here — let the JVM exit when all windows close.
            }
        });
        gameThread.setDaemon(true);
        gameThread.start();
        return gameThread;
    }

    private static void runSinglePlayer() {
        GameController game = new GameController();
        if (!game.createNewPlayer()) {
            return; // user cancelled name entry — gracefully return to menu
        }
        game.createNewGame();
    }

    private static void runLanHost(String[] args) throws Exception {
        GameSettings settings = GameSettings.get();
        int port = settings.getHostPort();
        if (args.length > 1) {
            port = Integer.parseInt(args[1]);
        }
        LanHostController host = new LanHostController(port);
        host.run();
    }

    private static void runLanClient() throws Exception {
        new LanClientController().run();
    }
}
