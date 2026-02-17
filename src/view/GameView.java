package view;

import model.Card;

import javax.swing.JOptionPane;
import java.util.ArrayList;

public class GameView {

    public String getUserName() {
        int attempts = 0;
        String userName;

        while (attempts < 3) {
            try {
                userName = JOptionPane.showInputDialog("Ingrese el nombre del usuario ");
                if (userName == null) {attempts++;JOptionPane.showMessageDialog(null, "Ingreso cancelado. Intento " + attempts + " de 3.");continue;}
                if (hasNumber(userName)) {attempts++;JOptionPane.showMessageDialog(null, "El nombre no puede contener números o caracteres especiales. Intento " + attempts + " de 3.");continue;}
                if (userName.isEmpty()) {attempts++;JOptionPane.showMessageDialog(null, "El nombre no puede esta vacio. Intento " + attempts + " de 3.");}
                return userName;

            } catch (Exception e) {
                attempts++;
                JOptionPane.showMessageDialog(null, "Error: " + e.getMessage() + ". Intento " + attempts + " de 3.");
            }
        }
        throw new IllegalStateException("Se superó el número máximo de intentos.");
    }


    private boolean hasNumber (String text){
        return text.matches(".*[\\d\\W].*");
    }

    public void showUserChips(String userName, int chips){
        JOptionPane.showMessageDialog(null,
                "Usuario: " + userName + "\n" +
                        "Cantidad de fichas: " + chips);
    }

    public void showPlayerHand(ArrayList<Card> hand) {
        StringBuilder handText = new StringBuilder("Tus cartas:\n");

        for (Card card : hand) {
            handText.append(card.toString()).append(" ");
        }
        JOptionPane.showMessageDialog(null, handText.toString());
    }

    public void showCommunityCards(ArrayList<Card> hand, ArrayList<Card> handUser, boolean hasTree){
        StringBuilder handText = new StringBuilder("Cartas de la Mesa:\n");
        StringBuilder handUserText = new StringBuilder("\nTus Cartas: ");

        for (Card card : hand) {
            handText.append(card.toString()).append(" ");
        }
        for (Card card : handUser) {
            handUserText.append(card.toString()).append(" ");
        }
        JOptionPane.showMessageDialog(null, handText.toString() + handUserText.toString());
        showPairCards(hasTree);
    }

    public static void showPairCards(boolean hasTree){
        if (hasTree){
            JOptionPane.showMessageDialog(null, "Hay un trio en la mesa");
        }else{
            JOptionPane.showMessageDialog(null, "No hay trio");
        }
    }

}
