package view;

import model.BettingRound;
import model.Card;
import model.PokerGame;

import javax.swing.JButton;
import javax.swing.JPanel;
import java.awt.Component;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class GameViewTest {

    public static void main(String[] args) {
        shouldShowCheckButtonForBigBlindEquivalentUnopenedPreflop();
        shouldClearRoundArtifactsInSingleResetCall();
        shouldBeIdempotentWhenResetCalledRepeatedly();
        System.out.println("GameViewTest: all tests passed");
    }

    private static void shouldShowCheckButtonForBigBlindEquivalentUnopenedPreflop() {
        GameView view = new GameView(false);
        GameView.TablePanel panel = attachTablePanel(view);

        BettingRound unopenedPreflop = new BettingRound(
            BettingRound.Phase.PREFLOP,
            30,
            PokerGame.BIG_BLIND,
            PokerGame.BIG_BLIND
        );

        panel.showBettingButtons(unopenedPreflop, PokerGame.BIG_BLIND, false, action -> {
        });

        JPanel bettingPanel = (JPanel) readField(panel, "bettingPanel");
        require(bettingPanel != null, "betting panel should be visible");
        require(hasButtonLabel(bettingPanel, "CHECK"), "CHECK should be visible for unopened preflop BB-equivalent state");
        require(!hasButtonLabel(bettingPanel, "CALL 0"), "CALL 0 should not be rendered when call amount is zero");
    }

    private static void shouldClearRoundArtifactsInSingleResetCall() {
        GameView view = new GameView(false);
        GameView.TablePanel panel = attachTablePanel(view);
        seedRoundArtifacts(panel);

        view.clearTableForNewHand();

        require(readListField(panel, "playerSprites").isEmpty(), "player sprites should clear");
        require(readListField(panel, "communitySprites").isEmpty(), "community sprites should clear");
        require(readListField(panel, "actionLog").isEmpty(), "action log should clear");
        require(!(boolean) readField(panel, "playerFolded"), "player folded marker should clear");
        require(readField(panel, "resultMessage") == null, "result message should clear");
        require((int) readField(panel, "pot") == 0, "pot overlay should clear");
        require(readField(panel, "bettingPanel") == null, "betting controls should clear");
    }

    private static void shouldBeIdempotentWhenResetCalledRepeatedly() {
        GameView view = new GameView(false);
        GameView.TablePanel panel = attachTablePanel(view);

        view.clearTableForNewHand();
        List<?> playerSpritesAfterFirst = readListField(panel, "playerSprites");
        List<?> communityAfterFirst = readListField(panel, "communitySprites");
        List<?> actionLogAfterFirst = readListField(panel, "actionLog");
        boolean foldedAfterFirst = (boolean) readField(panel, "playerFolded");
        Object resultAfterFirst = readField(panel, "resultMessage");
        int potAfterFirst = (int) readField(panel, "pot");

        view.clearTableForNewHand();

        require(readListField(panel, "playerSprites").isEmpty(), "second reset should keep player sprites empty");
        require(readListField(panel, "communitySprites").isEmpty(), "second reset should keep community empty");
        require(readListField(panel, "actionLog").isEmpty(), "second reset should keep action log empty");
        require(!(boolean) readField(panel, "playerFolded"), "second reset should keep folded marker clear");
        require(readField(panel, "resultMessage") == null, "second reset should keep result message clear");
        require((int) readField(panel, "pot") == 0, "second reset should keep pot at zero");
        require(readField(panel, "bettingPanel") == null, "second reset should keep betting controls clear");

        require(playerSpritesAfterFirst.isEmpty(), "first reset should leave empty player sprites state");
        require(communityAfterFirst.isEmpty(), "first reset should leave empty community state");
        require(actionLogAfterFirst.isEmpty(), "first reset should leave empty action log state");
        require(!foldedAfterFirst, "first reset should clear folded state");
        require(resultAfterFirst == null, "first reset should clear result message");
        require(potAfterFirst == 0, "first reset should clear pot");
    }

    private static GameView.TablePanel attachTablePanel(GameView view) {
        try {
            Constructor<GameView.TablePanel> constructor = GameView.TablePanel.class.getDeclaredConstructor(GameView.class);
            constructor.setAccessible(true);
            GameView.TablePanel panel = constructor.newInstance(view);

            Field tablePanelField = GameView.class.getDeclaredField("tablePanel");
            tablePanelField.setAccessible(true);
            tablePanelField.set(view, panel);
            return panel;
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("failed to create and attach table panel", ex);
        }
    }

    private static void seedRoundArtifacts(GameView.TablePanel panel) {
        panel.dealPlayerHand(new ArrayList<>(List.of(new Card("A", "S"), new Card("K", "D"))));
        panel.dealCommunity(new ArrayList<>(List.of(
            new Card("2", "H"),
            new Card("3", "C"),
            new Card("4", "D")
        )));
        panel.setActionLog(List.of("IA apuesta 20"));
        panel.setPlayerFolded();
        panel.setPot(135);
        panel.showResult(PokerGame.HandRank.ONE_PAIR, 135, true, null);

        panel.showBettingButtons(
            new BettingRound(BettingRound.Phase.PREFLOP, 0, 10, PokerGame.BIG_BLIND),
            0,
            false,
            action -> {
            }
        );

        require(!readListField(panel, "playerSprites").isEmpty(), "player sprites should be present before reset");
        require(!readListField(panel, "communitySprites").isEmpty(), "community sprites should be present before reset");
        require(!readListField(panel, "actionLog").isEmpty(), "action log should be present before reset");
        require((boolean) readField(panel, "playerFolded"), "player should be marked folded before reset");
        require(readField(panel, "resultMessage") != null, "result message should exist before reset");
        require((int) readField(panel, "pot") > 0, "pot should be present before reset");
        require(readField(panel, "bettingPanel") instanceof JPanel, "betting panel should exist before reset");
    }

    private static List<?> readListField(GameView.TablePanel panel, String fieldName) {
        Object value = readField(panel, fieldName);
        if (!(value instanceof List<?> list)) {
            throw new AssertionError("expected list field for " + fieldName);
        }
        return list;
    }

    private static Object readField(GameView.TablePanel panel, String fieldName) {
        try {
            Field field = GameView.TablePanel.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(panel);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("failed to read field: " + fieldName, ex);
        }
    }

    private static boolean hasButtonLabel(JPanel panel, String label) {
        for (Component component : panel.getComponents()) {
            if (component instanceof JButton button && label.equals(button.getText())) {
                return true;
            }
        }
        return false;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
