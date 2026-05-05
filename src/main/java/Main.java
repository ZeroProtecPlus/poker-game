import controller.GameController;

/*
 * To-do:
 * 1. Ya esta implementada la logica base de reparto, falta sincronizar con la UI
 * 2. Implementar paso a paso distribucion de cartas Turn, River y Flop
 * 3. Implementar Base de datos para manejo de fichas y usuarios
 * 4. Realizar pruebas de software, pruebas funcionales y no funcionales
 * 5. agregar dentro de JFX Swing una modal, para preguntar al usuario si desea continuar el game
 * 6. Buscar informacion acerca de las fuentes y diseño UI en JavaX Swing
 */
public class Main {

    public static void main(String[] args) {
        // La UI de Swing necesita el EDT libre.
        // Corremos la lógica del juego en un hilo separado
        // para que las animaciones y el repaint no se bloqueen.
        Thread gameThread = new Thread(() -> {
            try {
                GameController game = new GameController();
                game.createNewPlayer();
                game.createNewGame();
            } catch (IllegalStateException e) {
                javax.swing.SwingUtilities.invokeLater(() ->
                    javax.swing.JOptionPane.showMessageDialog(
                        null,
                        e.getMessage(),
                        "Cancelado",
                        javax.swing.JOptionPane.WARNING_MESSAGE
                    )
                );
            } catch (Exception e) {
                System.out.println(
                    "Ocurrió un error inesperado: " + e.getMessage()
                );
            }
        });

        gameThread.setDaemon(false); // mantiene viva la JVM mientras corre
        gameThread.start();
    }
}
