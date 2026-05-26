package view;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextInputDialog;
import javafx.stage.Window;
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
     * Shows the LAN connection dialog (IP + port) followed by the
     * casino-styled player name dialog, keeping both concerns separated.
     *
     * @return ConnectParams or null if cancelled at any step
     */
    public static ConnectParams showConnectDialog() {
        JavaFxBootstrap.ensureStarted();
        ConnectDialog.ConnectInfo info = ConnectDialog.showAndWaitBlocking();
        if (info == null) {
            return null;
        }
        String name = PlayerNameDialog.showAndWaitBlocking();
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        return new ConnectParams(info.host(), info.port(), name.trim());
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

    // ── Owner-aware overloads ──────────────────────────────────────────────

    /** Shows join rejection anchored to an owner window. */
    public static void showJoinRejection(JoinDecision decision, Window owner) {
        JavaFxBootstrap.ensureStarted();
        showAlert(Alert.AlertType.WARNING, "No se pudo unir", toJoinRejectionMessage(decision), owner);
    }

    /** Backward-compatible (unowned) overload. */
    public static void showJoinRejection(JoinDecision decision) {
        showJoinRejection(decision, null);
    }

    /** Shows retry-join confirmation anchored to an owner window. */
    public static boolean askRetryJoin(Window owner) {
        JavaFxBootstrap.ensureStarted();
        return showConfirmDialog("¿Intentar con otro nombre?", "Unirse a la partida", owner);
    }

    /** Backward-compatible (unowned) overload. */
    public static boolean askRetryJoin() {
        return askRetryJoin(null);
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

    public static void showLobbyWaiting(List<LobbyPlayer> players, boolean canStart, boolean isHost, Window owner) {
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
        showAlert(Alert.AlertType.INFORMATION, "Lobby LAN", message, owner);
    }

    public static void showLobbyWaiting(List<LobbyPlayer> players, boolean canStart, boolean isHost) {
        showLobbyWaiting(players, canStart, isHost, null);
    }

    public static boolean hostWantsToStart(boolean canStart, Window owner) {
        if (!canStart) {
            return false;
        }
        JavaFxBootstrap.ensureStarted();
        return showConfirmDialog("¿Iniciar la partida ahora?", "Lobby LAN", owner);
    }

    public static boolean hostWantsToStart(boolean canStart) {
        return hostWantsToStart(canStart, null);
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

    private static void showAlert(Alert.AlertType type, String title, String message, Window owner) {
        try {
            CompletableFuture<Void> future = new CompletableFuture<>();
            Platform.runLater(() -> {
                Alert alert = new Alert(type);
                if (owner != null) {
                    alert.initOwner(owner);
                }
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

    private static void showAlert(Alert.AlertType type, String title, String message) {
        showAlert(type, title, message, null);
    }

    private static boolean showConfirmDialog(String message, String title, Window owner) {
        try {
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                if (owner != null) {
                    alert.initOwner(owner);
                }
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

    private static boolean showConfirmDialog(String message, String title) {
        return showConfirmDialog(message, title, null);
    }
}
