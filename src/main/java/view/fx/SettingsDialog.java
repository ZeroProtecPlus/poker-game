package view.fx;

import config.GameSettings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import network.protocol.LanConstants;

import java.io.InputStream;
import java.util.concurrent.CompletableFuture;

public final class SettingsDialog {

    public enum Result {
        SAVED,
        CANCELLED
    }

    private static final String BG_PATH = "/sprites/Star_Game.png";
    private static final String FONTS_CSS_PATH = "/fonts.css";
    private static final String CSS_PATH = "/multiplayer-menu.css";
    private static final String CHROME_CSS_PATH = "/menu-chrome.css";
    private static final double DESIGN_WIDTH = MenuLayoutConstants.DESIGN_WIDTH;
    private static final double DESIGN_HEIGHT = MenuLayoutConstants.DESIGN_HEIGHT;

    private SettingsDialog() {}

    public static Result showAndWaitBlocking() {
        return JavaFxBootstrap.runOnFxAndWait(() -> {
            CompletableFuture<Result> future = new CompletableFuture<>();
            Stage stage = buildStage(future);
            stage.showAndWait();
            return future.getNow(Result.CANCELLED);
        });
    }

    private static Stage buildStage(CompletableFuture<Result> future) {
        GameSettings settings = GameSettings.get();

        Stage stage = new Stage(StageStyle.UNDECORATED);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Ajustes");

        ImageView background = imageViewAtNativeSize(loadImage(BG_PATH));

        Label title = new Label("Ajustes");
        title.getStyleClass().add("multiplayer-title");

        TextField portField = new TextField(String.valueOf(settings.getHostPort()));
        TextField clientHostField = new TextField(settings.getClientHost());
        TextField timeoutField = new TextField(String.valueOf(settings.getUiSyncTimeoutMs()));

        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(12);
        form.add(new Label("Puerto host (LAN):"), 0, 0);
        form.add(portField, 1, 0);
        form.add(new Label("IP predeterminada (cliente):"), 0, 1);
        form.add(clientHostField, 1, 1);
        form.add(new Label("Timeout UI (ms):"), 0, 2);
        form.add(timeoutField, 1, 2);

        for (var node : form.getChildren()) {
            if (node instanceof Label label) {
                label.setStyle("-fx-text-fill: #f5edd8; -fx-font-size: 16px;");
            }
            if (node instanceof TextField field) {
                field.setStyle("-fx-font-size: 16px; -fx-pref-width: 220;");
            }
        }

        Label hint = new Label("Puerto por defecto del proyecto: " + LanConstants.DEFAULT_PORT);
        hint.setStyle("-fx-text-fill: #c9a84c; -fx-font-size: 13px;");

        Button saveBtn = new Button("Guardar");
        saveBtn.getStyleClass().add("multiplayer-btn");
        FxMenuChrome.bindHoverShadow(saveBtn);
        saveBtn.setOnAction(event -> {
            if (applyFields(settings, portField, clientHostField, timeoutField)) {
                settings.save();
                complete(stage, future, Result.SAVED);
            }
        });

        Button cancelBtn = new Button("Volver");
        cancelBtn.getStyleClass().addAll("multiplayer-btn", "multiplayer-btn-back");
        FxMenuChrome.bindHoverShadow(cancelBtn);
        cancelBtn.setOnAction(event -> complete(stage, future, Result.CANCELLED));

        javafx.scene.layout.VBox panel = new javafx.scene.layout.VBox(
            16, title, form, hint, saveBtn, cancelBtn
        );
        panel.setAlignment(Pos.CENTER);
        panel.setPadding(new Insets(36));
        panel.setMaxWidth(640);
        panel.setStyle("-fx-background-color: rgba(6, 14, 10, 0.72); -fx-background-radius: 16;");

        AnchorPane canvas = new AnchorPane();
        canvas.setPrefSize(DESIGN_WIDTH, DESIGN_HEIGHT);
        canvas.setMinSize(DESIGN_WIDTH, DESIGN_HEIGHT);
        canvas.setMaxSize(DESIGN_WIDTH, DESIGN_HEIGHT);
        AnchorPane.setTopAnchor(background, 0.0);
        AnchorPane.setLeftAnchor(background, 0.0);
        canvas.getChildren().add(background);

        StackPane panelStack = new StackPane(panel);
        AnchorPane.setTopAnchor(panelStack, 0.0);
        AnchorPane.setBottomAnchor(panelStack, 0.0);
        AnchorPane.setLeftAnchor(panelStack, 0.0);
        AnchorPane.setRightAnchor(panelStack, 0.0);
        canvas.getChildren().add(panelStack);

        FxMenuChrome.apply(stage, canvas, "Ajustes");

        Scene scene = new Scene(canvas, DESIGN_WIDTH, DESIGN_HEIGHT, Color.BLACK);
        var fontsCss = SettingsDialog.class.getResource(FONTS_CSS_PATH);
        if (fontsCss != null) scene.getStylesheets().add(fontsCss.toExternalForm());
        var css = SettingsDialog.class.getResource(CSS_PATH);
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        var chromeCss = SettingsDialog.class.getResource(CHROME_CSS_PATH);
        if (chromeCss != null) {
            scene.getStylesheets().add(chromeCss.toExternalForm());
        }
        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                complete(stage, future, Result.CANCELLED);
            }
        });

        stage.setScene(scene);
        stage.setOnCloseRequest(event -> complete(stage, future, Result.CANCELLED));
        return stage;
    }

    private static boolean applyFields(
        GameSettings settings,
        TextField portField,
        TextField clientHostField,
        TextField timeoutField
    ) {
        try {
            int port = Integer.parseInt(portField.getText().trim());
            if (port < 1 || port > 65535) {
                showError("Puerto inválido (1–65535).");
                return false;
            }
            settings.setHostPort(port);
        } catch (NumberFormatException ex) {
            showError("Puerto inválido.");
            return false;
        }

        String host = clientHostField.getText().trim();
        if (host.isEmpty()) {
            showError("Indica una IP o hostname.");
            return false;
        }
        settings.setClientHost(host);

        try {
            settings.setUiSyncTimeoutMs(Long.parseLong(timeoutField.getText().trim()));
        } catch (NumberFormatException ex) {
            showError("Timeout inválido.");
            return false;
        }
        return true;
    }

    private static void showError(String message) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
            javafx.scene.control.Alert.AlertType.WARNING
        );
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private static ImageView imageViewAtNativeSize(Image image) {
        ImageView view = new ImageView(image);
        view.setPreserveRatio(false);
        view.setFitWidth(image.getWidth());
        view.setFitHeight(image.getHeight());
        return view;
    }

    private static Image loadImage(String classpathResource) {
        InputStream stream = SettingsDialog.class.getResourceAsStream(classpathResource);
        if (stream == null) {
            throw new IllegalStateException("Resource not found: " + classpathResource);
        }
        return new Image(stream);
    }

    private static void complete(Stage stage, CompletableFuture<Result> future, Result result) {
        if (!future.isDone()) {
            future.complete(result);
        }
        stage.close();
    }
}
