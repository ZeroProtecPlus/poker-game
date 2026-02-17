import javax.swing.JOptionPane;
import controller.GameController;

/* To-do

1. Implementar la logica para el reparto de cartas comunitarias y descicion de ganador
2. Implementar rondas de apuestas
3. Implementar distribucion de cartas Torn, River y Flop
4. Implementar manejo de apuestas, subir, duplicar y all in
5. Implementar Base de datos para manjo de fichas y usuarios
6 Implementar interfaz grafica con JavaSwing

*/

public class Main {
    public static void main(String[] args) {

        try {
            GameController game = new GameController();
            game.createNewPlayer();
            game.viewUserChips();
            game.createNewGame();

        } catch (IllegalStateException e) {
            JOptionPane.showMessageDialog(null, e.getMessage(), "Cancelado", JOptionPane.WARNING_MESSAGE);
        } catch (Exception e) {
            System.out.println("Ocurrió un error inesperado: "+ e.getMessage() + "Error");
        }

    }
}