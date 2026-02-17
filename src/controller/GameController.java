package controller;

import model.*;
import view.GameView;

import java.util.ArrayList;

public class GameController {
    private PokerGame pokerGame;
    private String userNamePlayer;
    private User newPlayer;
    private final GameView newGame;

//Constructor Class
    public GameController() {
        this.newGame = new GameView();
    }

    public void createNewPlayer(){
        userNamePlayer = newGame.getUserName();
        this.newPlayer = new User(userNamePlayer);
    }

    public void viewUserChips() {
        newGame.showUserChips(userNamePlayer, newPlayer.getNumbChips());
    }

    public void createNewGame(){
        this.pokerGame = new PokerGame(newPlayer);

        pokerGame.startNewRound(); // Primero empieza la ronda y reparte
        ArrayList<Card> manoJugador = pokerGame.getPlayerHand(); // Obtener la mano que ya tiene cartas
        newGame.showPlayerHand(manoJugador);


        pokerGame.dealFlop();
        pokerGame.dealTurnOrRiver();
        pokerGame.dealTurnOrRiver();

    ArrayList<Card> communityCards = pokerGame.getCommunityCards();
    boolean hasTrio = pokerGame.communityHasTrips();
    newGame.showCommunityCards(communityCards, manoJugador, hasTrio);
    }

}
