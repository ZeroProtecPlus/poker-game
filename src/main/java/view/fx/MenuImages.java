package view.fx;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.io.InputStream;

final class MenuImages {

    private MenuImages() {}

    static Image load(String classpathResource) {
        InputStream stream = MenuImages.class.getResourceAsStream(classpathResource);
        if (stream == null) {
            throw new IllegalStateException("Resource not found: " + classpathResource);
        }
        Image image = new Image(stream);
        if (image.isError()) {
            throw new IllegalStateException("Failed to load image: " + classpathResource);
        }
        return image;
    }

    /** Capa PNG completa 0,0 — zonas transparentes dejan ver el fondo. */
    static ImageView fullCanvasLayer(String classpathResource) {
        Image image = load(classpathResource);
        ImageView view = new ImageView(image);
        view.setPreserveRatio(false);
        view.setFitWidth(image.getWidth());
        view.setFitHeight(image.getHeight());
        view.setMouseTransparent(true);
        view.setPickOnBounds(false);
        view.getStyleClass().add("menu-full-canvas-layer");
        return view;
    }

    static ImageView nativeSizedView(Image image, String styleClass) {
        ImageView view = new ImageView(image);
        view.setPreserveRatio(false);
        view.setFitWidth(image.getWidth());
        view.setFitHeight(image.getHeight());
        if (styleClass != null) {
            view.getStyleClass().add(styleClass);
        }
        return view;
    }
}
