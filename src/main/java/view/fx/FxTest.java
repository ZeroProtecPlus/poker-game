package view.fx;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * Minimal JavaFX window test - isolates rendering from GameTable.
 */
public class FxTest extends Application {

    @Override
    public void start(Stage stage) {
        Rectangle r = new Rectangle(600, 400, Color.DARKGREEN);
        Text t = new Text("JAVAFX IS WORKING");
        t.setFill(Color.WHITE);
        t.setStyle("-fx-font-size: 32px; -fx-font-weight: bold;");

        StackPane root = new StackPane(r, t);
        Scene scene = new Scene(root, 600, 400, Color.BLACK);

        stage.initStyle(StageStyle.DECORATED);
        stage.setTitle("JavaFX Test");
        stage.setScene(scene);
        stage.centerOnScreen();
        stage.show();
        stage.toFront();
        System.out.println("[FxTest] Stage shown at (" +
            stage.getX() + "," + stage.getY() + ") " +
            stage.getWidth() + "x" + stage.getHeight());
    }

    public static void main(String[] args) {
        launch(args);
    }
}
