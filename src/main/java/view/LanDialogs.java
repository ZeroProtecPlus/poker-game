package view;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextInputDialog;
import network.contracts.JoinDecision;
import network.contracts.RejectReason;
import network.protocol.JoinPayloads.LobbyPlayer;
import network.protocol.LanConstants;
import view.fx.ConnectDialog;
import view.fx.JavaFxBootstrap;
import view.fx.PlayerNameDialog;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public final class LanDialogs {

    private LanDialogs() {}

    public record ConnectParams(String host, int port, String playerName) {}

    /**
     * Shows the unified LAN connection dialog (replaces the old 3-popup flow).
     * Single casino-styled form with IP, port, and player name fields
     * with inline validation.
     *
     * @return ConnectParams or null if cancelled
     */
    public static ConnectParams showConnectDialog() {
        JavaFxBootstrap.ensureStarted();
        return ConnectDialog.showAndWaitBlocking();
    }

    public static String showHostNameDialog() {
        String name = PlayerNameDialog.showAndWaitBlocking();
        return name != null ? name.trim() : "";
    }

    public static int showHostPortDialog() {
        JavaFxBootstrap.ensureStarted();
        String portText = showTextInputDialog(
            "Puerto TCP para LAN:",
            String.valueOf(LanConstants.DEFAULT_PORT)
        );
        if (portText == null || portText.isBlank()) {
            return LanConstants.DEFAULT_PORT;
        }
        try {
            return Integer.parseInt(portText.trim());
        } catch (NumberFormatException ex) {
            return LanConstants.DEFAULT_PORT;
        }
    }

    public static void showJoinRejection(JoinDecision decision) {
        JavaFxBootstrap.ensureStarted();
        showAlert(Alert.AlertType.WARNING, "No se pudo unir", toJoinRejectionMessage(decision));
    }

    public static boolean askRetryJoin() {
        JavaFxBootstrap.ensureStarted();
        return showConfirmDialog("¿Intentar con otro nombre?", "Unirse a la partida");
    }

    public static String toJoinRejectionMessage(JoinDecision decision) {
        RejectReason reasonCode = decision.getReasonCode();
        if (reasonCode == RejectReason.NAME_TAKEN) {
            return "Ese nombre ya está en uso en la sesión. Probá con otro.";
        }
        if (reasonCode == RejectReason.INVALID_FORMAT) {
            return "Nombre inválido. Usa solo letras (3 a 16 caracteres).";
        }
        String detail = decision.getDetail();
        if (detail != null && detail.contains("llena")) {
            return detail;
        }
        return "No se pudo unir a la sesión. Intenta nuevamente.";
    }

    public static void showLobbyWaiting(List<LobbyPlayer> players, boolean canStart, boolean isHost) {
        JavaFxBootstrap.ensureStarted();
        String roster = players.stream()
            .map(LobbyPlayer::displayName)
            .collect(Collectors.joining("\n"));
        String message = "Jugadores en la mesa:\n" + roster
            + "\n\nMínimo para empezar: " + LanConstants.MIN_PLAYERS_TO_START
            + "\nMáximo: " + LanConstants.MAX_HUMAN_PLAYERS;
        if (isHost) {
            message += canStart
                ? "\n\nPulsa OK para iniciar la partida."
                : "\n\nEsperando más jugadores… Pulsa OK para actualizar.";
        } else {
            message += "\n\nEsperando a que el host inicie la partida…";
        }
        showAlert(Alert.AlertType.INFORMATION, "Lobby LAN", message);
    }

    public static boolean hostWantsToStart(boolean canStart) {
        if (!canStart) {
            return false;
        }
        JavaFxBootstrap.ensureStarted();
        return showConfirmDialog("¿Iniciar la partida ahora?", "Lobby LAN");
    }

    // ── JavaFX dialog helpers (called from game threads) ──

    private static String showTextInputDialog(String prompt, String defaultValue) {
        try {
            CompletableFuture<String> future = new CompletableFuture<>();
            Platform.runLater(() -> {
                TextInputDialog dialog = new TextInputDialog(defaultValue);
                dialog.setTitle("Royal Poker");
                dialog.setHeaderText(null);
                dialog.setContentText(prompt);
                Optional<String> result = dialog.showAndWait();
                future.complete(result.orElse(null));
            });
            return future.get();
        } catch (Exception ex) {
            System.err.println("JavaFX text dialog failed: " + ex.getMessage());
            return null;
        }
    }

    private static void showAlert(Alert.AlertType type, String title, String message) {
        try {
            CompletableFuture<Void> future = new CompletableFuture<>();
            Platform.runLater(() -> {
                Alert alert = new Alert(type);
                alert.setTitle(title);
                alert.setHeaderText(null);
                alert.setContentText(message);
                alert.showAndWait();
                future.complete(null);
            });
            future.get();
        } catch (Exception ex) {
            System.err.println("JavaFX alert failed: " + ex.getMessage());
        }
    }

    private static boolean showConfirmDialog(String message, String title) {
        try {
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                alert.setTitle(title);
                alert.setHeaderText(null);
                alert.setContentText(message);
                alert.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);
                Optional<ButtonType> result = alert.showAndWait();
                future.complete(result.isPresent() && result.get() == ButtonType.YES);
            });
            return future.get();
        } catch (Exception ex) {
            System.err.println("JavaFX confirm dialog failed: " + ex.getMessage());
            return false;
        }
    }
}
