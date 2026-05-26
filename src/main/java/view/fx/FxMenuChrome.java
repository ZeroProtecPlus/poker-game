package view.fx;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * Custom title bar (minimize / close) for undecorated menu stages.
 */
public final class FxMenuChrome {

    private static final double CHROME_HEIGHT = MenuLayoutConstants.CHROME_HEIGHT;

    private FxMenuChrome() {}

    public static void apply(Stage stage, AnchorPane root, String title) {
        apply(stage, root, title, MenuLayoutConstants.DESIGN_WIDTH, MenuLayoutConstants.DESIGN_HEIGHT);
    }

    public static void apply(Stage stage, AnchorPane root, String title, double width, double height) {
        stage.setResizable(false);
        stage.setMinWidth(width);
        stage.setMaxWidth(width);
        stage.setMinHeight(height);
        stage.setMaxHeight(height);

        HBox bar = buildTitleBar(stage, title);
        AnchorPane.setTopAnchor(bar, 0.0);
        AnchorPane.setLeftAnchor(bar, 0.0);
        AnchorPane.setRightAnchor(bar, 0.0);
        bar.setPrefHeight(CHROME_HEIGHT);
        bar.setMaxHeight(CHROME_HEIGHT);
        bar.toFront();
        root.getChildren().add(bar);
    }

    public static void apply(Stage stage, StackPane root, String title) {
        apply(stage, root, title, MenuLayoutConstants.DESIGN_WIDTH, MenuLayoutConstants.DESIGN_HEIGHT);
    }

    public static void apply(Stage stage, StackPane root, String title, double width, double height) {
        HBox bar = buildTitleBar(stage, title);
        bar.setPrefHeight(CHROME_HEIGHT);
        bar.setMaxHeight(CHROME_HEIGHT);
        bar.setMinHeight(CHROME_HEIGHT);
        bar.setManaged(false);
        bar.setLayoutX(0);
        bar.setLayoutY(0);
        bar.toFront();
        root.getChildren().add(bar);
    }

    private static HBox buildTitleBar(Stage stage, String title) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("menu-chrome-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button minimizeBtn = chromeButton("—");
        minimizeBtn.setOnAction(event -> stage.setIconified(true));

        Button closeBtn = chromeButton("✕");
        closeBtn.getStyleClass().add("menu-chrome-close");
        closeBtn.setOnAction(event -> stage.close());

        HBox bar = new HBox(8, titleLabel, spacer, minimizeBtn, closeBtn);
        bar.getStyleClass().add("menu-chrome-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPrefHeight(CHROME_HEIGHT);
        bar.setMaxHeight(CHROME_HEIGHT);
        return bar;
    }

    private static Button chromeButton(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("menu-chrome-btn");
        return button;
    }

    public static void bindHoverShadow(Region hitZone) {
        DropShadow shadow = new DropShadow();
        shadow.setRadius(10);
        shadow.setSpread(0.12);
        shadow.setOffsetX(0);
        shadow.setOffsetY(5);
        shadow.setColor(Color.color(0, 0, 0, 0.55));

        hitZone.setOnMouseEntered(event -> hitZone.setEffect(shadow));
        hitZone.setOnMouseExited(event -> hitZone.setEffect(null));
    }
}
