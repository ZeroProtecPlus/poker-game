import controller.GameController;

/*
 * To-do:
 * 1. Implementar la logica para el reparto de cartas comunitarias y decision de ganador
 * 2. Implementar rondas de apuestas
 * 3. Implementar distribucion de cartas Turn, River y Flop
 * 4. Implementar manejo de apuestas: subir, duplicar y all in
 * 5. Implementar Base de datos para manejo de fichas y usuarios
 * 6. Implementar interfaz grafica con JavaSwing  ✓
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
                game.viewUserChips();
                game.createNewGame();
            } catch (IllegalStateException e) {
                javax.swing.SwingUtilities.invokeLater(() ->
                    javax.swing.JOptionPane.showMessageDialog(
                        null, e.getMessage(), "Cancelado",
                        javax.swing.JOptionPane.WARNING_MESSAGE
                    )
                );
            } catch (Exception e) {
                System.out.println("Ocurrió un error inesperado: " + e.getMessage());
            }
        });

        gameThread.setDaemon(false); // mantiene viva la JVM mientras corre
        gameThread.start();
    }
}