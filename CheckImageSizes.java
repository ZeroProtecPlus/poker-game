import javax.imageio.ImageIO;
import java.io.InputStream;
import java.awt.image.BufferedImage;

public class CheckImageSizes {
    public static void main(String[] args) throws Exception {
        String[] paths = {"/menu-bg.png", "/menu_btn_entrar.png", "/menu_input.png"};
        for (String p : paths) {
            InputStream s = CheckImageSizes.class.getResourceAsStream(p);
            if (s != null) {
                BufferedImage img = ImageIO.read(s);
                System.out.println(p + ": " + img.getWidth() + "x" + img.getHeight());
            } else {
                System.out.println(p + ": NOT FOUND on classpath");
            }
        }
    }
}
