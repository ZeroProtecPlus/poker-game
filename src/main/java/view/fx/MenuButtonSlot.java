package view.fx;

import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;

/**
 * Botón de menú: capa PNG completa (transparencia) + zona de clic con hover.
 */
public final class MenuButtonSlot {

    private MenuButtonSlot() {}

    /**
     * Añade capa PNG a tamaño completo del lienzo (0,0). Solo el arte opaco del botón se ve;
     * el resto es transparente y deja ver capas inferiores.
     */
    public static ImageView addFullCanvasOverlay(AnchorPane canvas, String overlayClasspath) {
        ImageView layer = MenuImages.fullCanvasLayer(overlayClasspath);
        AnchorPane.setTopAnchor(layer, 0.0);
        AnchorPane.setLeftAnchor(layer, 0.0);
        canvas.getChildren().add(layer);
        return layer;
    }

    /**
     * Zona interactiva alineada a los píxeles opacos del botón (hover + clic).
     */
    public static StackPane addHitZone(
        AnchorPane canvas,
        double x,
        double y,
        double width,
        double height,
        Runnable onClick
    ) {
        Region shadowPlate = new Region();
        shadowPlate.setPrefSize(width, height);
        shadowPlate.setMaxSize(width, height);
        shadowPlate.setMouseTransparent(true);
        shadowPlate.getStyleClass().add("menu-button-shadow-plate");

        StackPane slot = new StackPane(shadowPlate);
        slot.setPrefSize(width, height);
        slot.setMaxSize(width, height);
        slot.setLayoutX(x);
        slot.setLayoutY(y);
        slot.setCursor(Cursor.HAND);
        slot.getStyleClass().add("menu-button-slot");

        bindHover(slot, shadowPlate, width, height);
        slot.setOnMouseClicked(event -> onClick.run());

        canvas.getChildren().add(slot);
        return slot;
    }

    /** Recorte local (p. ej. player name) cuando el arte está en un rectángulo del mismo PNG. */
    public static StackPane addCroppedArtSlot(
        AnchorPane canvas,
        String overlayClasspath,
        double cropX,
        double cropY,
        double cropW,
        double cropH,
        Runnable onClick
    ) {
        Image image = MenuImages.load(overlayClasspath);
        ImageView art = new ImageView(image);
        art.setViewport(new Rectangle2D(cropX, cropY, cropW, cropH));
        art.setPreserveRatio(false);
        art.setFitWidth(cropW);
        art.setFitHeight(cropH);
        art.setMouseTransparent(true);
        art.getStyleClass().add("menu-button-art");

        Region shadowPlate = new Region();
        shadowPlate.setPrefSize(cropW, cropH);
        shadowPlate.setMaxSize(cropW, cropH);
        shadowPlate.setMouseTransparent(true);

        StackPane slot = new StackPane(shadowPlate, art);
        slot.setPrefSize(cropW, cropH);
        slot.setMaxSize(cropW, cropH);
        slot.setLayoutX(cropX);
        slot.setLayoutY(cropY);
        slot.setCursor(Cursor.HAND);
        bindHover(slot, shadowPlate, cropW, cropH);
        slot.setOnMouseClicked(event -> onClick.run());
        canvas.getChildren().add(slot);
        return slot;
    }

    private static void bindHover(
        StackPane slot,
        Region shadowPlate,
        double width,
        double height
    ) {
        DropShadow drop = new DropShadow();
        drop.setRadius(Math.max(12, height / 6));
        drop.setSpread(0.15);
        drop.setOffsetX(0);
        drop.setOffsetY(5);
        drop.setColor(Color.color(0, 0, 0, 0.72));

        Runnable enter = () -> {
            shadowPlate.setStyle(
                "-fx-background-color: rgba(0, 0, 0, 0.28);"
                    + "-fx-background-radius: " + Math.min(16, height / 4) + ";"
            );
            shadowPlate.setEffect(drop);
        };
        Runnable exit = () -> {
            shadowPlate.setStyle("-fx-background-color: transparent;");
            shadowPlate.setEffect(null);
        };

        slot.setOnMouseEntered(event -> enter.run());
        slot.setOnMouseExited(event -> exit.run());
    }
}
