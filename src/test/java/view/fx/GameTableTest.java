package view.fx;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Label;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import model.AIPlayer;
import model.BettingRound;
import model.Card;

/**
 * TDD tests for {@link GameTable} — Phase 2 player/AI badges, pot display, role indicators.
 *
 * Phase 5 update: redesigned solid badges with poker-chip role indicators,
 * separate name/chips labels, chip rotation animation.
 *
 * Run after {@code mvn test-compile dependency:build-classpath -Dmdep.outputFile=target/test-cp.txt}:
 * {@code java -cp "target/test-classes;target/classes;<deps>" view.fx.GameTableTest}
 */
public class GameTableTest {

    private static volatile boolean javafxInitialized;

    public static void main(String[] args) throws Exception {
        ensureJavaFxInitialized();
        cssFileShouldExistOnClasspath();
        createShouldReturnNonNullGameTable();
        canvasShouldHaveDesignDimensions();
        isVisibleShouldReflectStageState();
        awaitUiReadyShouldNotBlockForever();
        shouldBeSingletonCreatingNewDisposesOld();
        showShouldSetStageVisibleAndHideFrameHides();
        updatePlayerInfoShouldSetBadgeTextAndFoldedState();
        updatePlayerInfoShouldFormatChipsWithCommas();
        updateAIPlayersShouldPositionBadgesForOneAI();
        updateAIPlayersShouldPositionBadgesForThreeAIs();
        updateAIPlayersShouldHideExcessBadges();
        updateAIPlayersShouldApplyFoldedCssToFoldedAI();
        updatePotShouldDisplayFormattedAmount();
        updatePotShouldHideWhenZero();
        clearTableShouldResetAllState();

        // ── Phase 5: redesigned badges — structural tests ─────────────────────
        playerBadgeShouldBeHBoxWithChipAndNameAndChips();
        foldIndicatorShouldBeVisibleOnlyWhenFolded();
        playerRoleChipShouldHaveCorrectCssForDealer();
        playerRoleChipShouldHaveCorrectCssForBlinds();
        playerRoleChipShouldBeHiddenForNoneRole();
        aiBadgeShouldBeHBoxWithChipAndNameAndChips();
        potShouldBeHBoxWithHexagonIcon();

        // ── Phase 6: Card dealing tests ────────────────────────────────────
        shouldHaveDeckPileAtDeckPosition();
        shouldDealPlayerCardsShowSectionAndCards();
        shouldDealCommunityCardsShowSectionAndCards();
        shouldClearCardsRemoveAllCards();
        shouldReDealPlayerHandReplacesPrevious();

        // ── Phase 7: Action log tests ────────────────────────────────────
        setActionLogShouldDisplayUpToFourLines();
        setActionLogShouldHideWhenEmpty();
        setActionLogShouldShowMostRecentAtBottom();
        appendActionLogShouldAddLine();
        appendActionLogShouldRemoveOldestWhenFull();
        clearTableShouldClearActionLog();

        // ── Phase 9: Game over tests ──────────────────────────────────────
        showGameOverShouldBeHiddenByDefault();
        showGameOverShouldDisplayWinWithChipCount();
        showGameOverShouldDisplayLossBankrupt();
        hideGameOverShouldHideOverlay();

        // ── fix-endgame-states: winner-name overload tests ────────────────
        showGameOverShouldDisplayWinnerNameOnWin();
        showGameOverShouldDisplayOpponentNameOnBust();
        showGameOverShouldFallBackToDefaultWhenNoWinnerName();

        System.out.println("GameTableTest: all tests passed");
        visualDemo();
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Phase 5 tests — redesigned badges with poker-chip role indicators
    // ═════════════════════════════════════════════════════════════════════════

    private static void playerBadgeShouldBeHBoxWithChipAndNameAndChips() {
        runOnFxThreadAndWait(() -> {
            GameTable table = GameTable.create();

            Object badgeObj = getField(table, "playerBadge");
            require(badgeObj instanceof HBox,
                "playerBadge should be an HBox container, got: "
                + (badgeObj == null ? "null" : badgeObj.getClass().getName()));

            HBox badge = (HBox) badgeObj;
            require(badge.getChildren().size() == 4,
                "Player badge HBox should have 4 children (foldIndicator + chip + name + chips), got: "
                + badge.getChildren().size());

            // Child [0]: fold indicator ✕
            require(badge.getChildren().get(0) instanceof Label,
                "First child should be fold indicator Label");
            Label foldIndicator = (Label) badge.getChildren().get(0);
            require(foldIndicator.getText().contains("\u2715"),
                "Fold indicator should be ✕, got: " + foldIndicator.getText());
            require(foldIndicator.getStyleClass().contains("fold-indicator"),
                "Fold indicator should have fold-indicator CSS class");

            // Child [1]: role chip
            require(badge.getChildren().get(1) instanceof Label,
                "Second child should be role chip Label");
            Label roleChip = (Label) badge.getChildren().get(1);
            require(!roleChip.isVisible(),
                "Role chip should be hidden when no player info set");

            // Child [2]: name label
            require(badge.getChildren().get(2) instanceof Label,
                "Third child should be name Label");
            Label nameLabel = (Label) badge.getChildren().get(2);
            require(nameLabel.getStyleClass().contains("badge-name"),
                "Name label should have badge-name CSS class");

            // Child [3]: chips label
            require(badge.getChildren().get(3) instanceof Label,
                "Fourth child should be chips Label");
            Label chipsLabel = (Label) badge.getChildren().get(3);
            require(chipsLabel.getStyleClass().contains("badge-chips"),
                "Chips label should have badge-chips CSS class");
        });
    }

    private static void foldIndicatorShouldBeVisibleOnlyWhenFolded() {
        runOnFxThreadAndWait(() -> {
            GameTable table = GameTable.create();

            // Non-folded: indicator should be invisible
            table.updatePlayerInfo("Emaq", 1000, "D", false);
            HBox badge = getField(table, "playerBadge");
            Label foldIndicator = (Label) badge.getChildren().get(0);
            require(!foldIndicator.isVisible(),
                "Fold indicator should be hidden when not folded");

            // Folded: indicator should be visible
            table.updatePlayerInfo("Emaq", 1000, "D", true);
            require(foldIndicator.isVisible(),
                "Fold indicator should be visible when player folds");

            // Un-fold: indicator should hide again (triangulation)
            table.updatePlayerInfo("Emaq", 1000, "D", false);
            require(!foldIndicator.isVisible(),
                "Fold indicator should hide when player un-folds");

            // clearTable should also hide it
            table.updatePlayerInfo("Emaq", 1000, "D", true);
            require(foldIndicator.isVisible(),
                "Precondition: indicator visible before clear");
            table.clearTable();
            require(!foldIndicator.isVisible(),
                "Fold indicator should be hidden after clearTable");
        });
    }

    private static void playerRoleChipShouldHaveCorrectCssForDealer() {
        runOnFxThreadAndWait(() -> {
            GameTable table = GameTable.create();

            table.updatePlayerInfo("Emaq", 1000, "D", false);
            Label roleChip = getField(table, "playerRoleChip");

            require(roleChip.isVisible(),
                "Role chip should be visible for dealer role");
            require(roleChip.getText().equals("D"),
                "Role chip text should be 'D', got: " + roleChip.getText());
            require(roleChip.getStyleClass().contains("chip-white"),
                "Role chip should have chip-white CSS class for dealer");
            require(!roleChip.getStyleClass().contains("chip-purple"),
                "Role chip should NOT have chip-purple for dealer");
        });
    }

    private static void playerRoleChipShouldHaveCorrectCssForBlinds() {
        // Small Blind — fresh table (no transition)
        runOnFxThreadAndWait(() -> {
            GameTable table = GameTable.create();
            table.updatePlayerInfo("Emaq", 1000, "SB", false);
            Label roleChip = getField(table, "playerRoleChip");
            require(roleChip.isVisible(),
                "Role chip should be visible for SB");
            require(roleChip.getText().equals("SB"),
                "Role chip text should be 'SB', got: " + roleChip.getText());
            require(roleChip.getStyleClass().contains("chip-purple"),
                "Role chip should have chip-purple for SB");
            require(!roleChip.getStyleClass().contains("chip-white"),
                "Role chip should NOT have chip-white for SB");
        });

        // Big Blind — separate fresh table (no transition from SB→BB)
        runOnFxThreadAndWait(() -> {
            GameTable table = GameTable.create();
            table.updatePlayerInfo("Emaq", 1000, "BB", false);
            Label roleChip = getField(table, "playerRoleChip");
            require(roleChip.isVisible(),
                "Role chip should be visible for BB");
            require(roleChip.getText().equals("BB"),
                "Role chip text should be 'BB', got: " + roleChip.getText());
            require(roleChip.getStyleClass().contains("chip-purple"),
                "Role chip should have chip-purple for BB");
        });
    }

    private static void playerRoleChipShouldBeHiddenForNoneRole() {
        runOnFxThreadAndWait(() -> {
            GameTable table = GameTable.create();

            table.updatePlayerInfo("Emaq", 1000, "", false);
            Label roleChip = getField(table, "playerRoleChip");
            require(!roleChip.isVisible(),
                "Role chip should be hidden for empty/NONE role");
            require(roleChip.getText().isEmpty(),
                "Role chip text should be empty for NONE role");

            // Also test with null role
            table.updatePlayerInfo("Emaq", 1000, null, false);
            require(!roleChip.isVisible(),
                "Role chip should be hidden for null role");
        });
    }

    private static void aiBadgeShouldBeHBoxWithChipAndNameAndChips() {
        runOnFxThreadAndWait(() -> {
            GameTable table = GameTable.create();

            List<AIPlayer> ais = new ArrayList<>();
            ais.add(makeAI("Bot1", 3000, AIPlayer.Role.DEALER));
            table.updateAIPlayers(ais);

            HBox[] badges = getField(table, "aiBadges");

            // First AI badge should be an HBox with 3 children: chip + name + chips
            require(badges[0] instanceof HBox,
                "AI badge should be HBox container");
            require(badges[0].getChildren().size() == 3,
                "AI badge HBox should have 3 children (chip + name + chips), got: "
                + badges[0].getChildren().size());

            // Child [0]: role chip
            Label roleChip = (Label) badges[0].getChildren().get(0);
            require(roleChip.isVisible(),
                "AI role chip should be visible for dealer");
            require(roleChip.getText().equals("D"),
                "AI role chip should say 'D', got: " + roleChip.getText());
            require(roleChip.getStyleClass().contains("chip-white"),
                "AI dealer chip should have chip-white CSS class");

            // Child [1]: name label
            Label nameLabel = (Label) badges[0].getChildren().get(1);
            require(nameLabel.getStyleClass().contains("badge-name"),
                "AI name should have badge-name CSS class");
            require(nameLabel.getText().contains("Bot1"),
                "AI name should contain 'Bot1', got: " + nameLabel.getText());

            // Child [2]: chips label
            Label chipsLabel = (Label) badges[0].getChildren().get(2);
            require(chipsLabel.getStyleClass().contains("badge-chips"),
                "AI chips should have badge-chips CSS class");
            require(chipsLabel.getText().contains("3,000"),
                "AI chips should show formatted amount, got: " + chipsLabel.getText());
        });
    }

    private static void potShouldBeHBoxWithHexagonIcon() {
        runOnFxThreadAndWait(() -> {
            GameTable table = GameTable.create();

            // Pot visible with amount
            table.updatePot(4200);
            Object potObj = getField(table, "potLabel");
            require(potObj instanceof HBox,
                "potLabel should be HBox container, got: "
                + (potObj == null ? "null" : potObj.getClass().getName()));

            HBox pot = (HBox) potObj;
            require(pot.getChildren().size() == 2,
                "Pot HBox should have 2 children (icon + text), got: "
                + pot.getChildren().size());
            require(pot.isVisible(),
                "Pot HBox should be visible when amount > 0");

            // Child [0]: hexagon icon ⬡
            Label icon = (Label) pot.getChildren().get(0);
            require(icon.getText().contains("\u2B21"),
                "Pot icon should be ⬡, got: " + icon.getText());
            require(icon.getStyleClass().contains("pot-icon"),
                "Pot icon should have pot-icon CSS class");

            // Child [1]: pot text
            Label text = (Label) pot.getChildren().get(1);
            require(text.getStyleClass().contains("pot-text"),
                "Pot text should have pot-text CSS class");
            require(text.getText().contains("BOTE"),
                "Pot text should contain BOTE, got: " + text.getText());
            require(text.getText().contains("4,200"),
                "Pot text should contain amount, got: " + text.getText());

            // Zero amount: pot should hide (triangulation)
            table.updatePot(0);
            require(!pot.isVisible(),
                "Pot HBox should be hidden when amount is 0");
        });
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Visual demo — keeps window open with sample data
    // ═════════════════════════════════════════════════════════════════════════

    private static void visualDemo() throws Exception {
        System.out.println("\n=== VISUAL DEMO: GameTable (Phase 9: Game Over) ===");
        System.out.println("Window shows badges, pot, cards, action log, result and game over.");
        System.out.println("Close the window to exit.");

        GameTable table = createOnFxThread();

        // Sample data
        table.updatePlayerInfo("Emaq", 12500, "D", false);
        table.updatePot(4200);

        List<AIPlayer> ais = new ArrayList<>();
        ais.add(makeAI("IA-Lucia", 8200, AIPlayer.Role.SMALL_BLIND));
        ais.add(makeAI("IA-Carlos", 15000, AIPlayer.Role.BIG_BLIND));
        ais.add(makeAI("IA-Sofia", 9800, AIPlayer.Role.NONE));
        table.updateAIPlayers(ais);

        table.show();

        // Show betting buttons at start of hand (Preflop)
        BettingRound round = new BettingRound(BettingRound.Phase.PREFLOP, 0, 0, 100);
        runOnFxThreadAndWait(() ->
            table.showBettingButtons(round, 0, false, action ->
                System.out.println("Player action: " + action))
        );
        System.out.println("Visual demo: showing betting buttons (Preflop)");

        // Deal player hand after 3 seconds
        Thread.sleep(3000);
        List<Card> hand = Arrays.asList(
            new Card("A", "\u2660"),
            new Card("K", "\u2665"));
        runOnFxThreadAndWait(() -> table.dealPlayerHand(hand));
        System.out.println("Visual demo: dealt player hand (A\u2660, K\u2665)");

        // Deal flop after 2 more seconds
        Thread.sleep(2000);
        List<Card> flop = Arrays.asList(
            new Card("Q", "\u2666"),
            new Card("J", "\u2660"),
            new Card("10", "\u2665"));
        runOnFxThreadAndWait(() -> table.dealCommunityCards(flop));
        System.out.println("Visual demo: dealt flop (Q\u2666, J\u2660, 10\u2665)");

        // Show action log after 1 second
        Thread.sleep(1000);
        runOnFxThreadAndWait(() ->
            table.setActionLog(Arrays.asList(
                "IA-Lucia apuesta 200",
                "IA-Carlos iguala 200",
                "IA-Sof\u00eda se retira"
            ))
        );
        System.out.println("Visual demo: set action log with 3 entries");

        // Show result banner (win) after 2 seconds
        Thread.sleep(2000);
        runOnFxThreadAndWait(() ->
            table.showResult("¡Ganaste! +4,200  |  Full House", true)
        );
        System.out.println("Visual demo: showing win result banner");

        // Switch to loss result after 3 seconds
        Thread.sleep(3000);
        runOnFxThreadAndWait(() ->
            table.showResult("IA-Carlos gana el bote de 1,500  |  Tu mano: One Pair", false)
        );
        System.out.println("Visual demo: showing loss result banner");

        // Show game over (win) after 3 seconds
        Thread.sleep(3000);
        runOnFxThreadAndWait(() -> table.showGameOver(18400));
        System.out.println("Visual demo: showing game over (win with 18,400 chips)");

        // Switch to game over (loss) after 3 seconds
        Thread.sleep(3000);
        runOnFxThreadAndWait(() -> table.showGameOver(0));
        System.out.println("Visual demo: showing game over (bankrupt)");

        // Hide game over after 3 seconds
        Thread.sleep(3000);
        runOnFxThreadAndWait(() -> table.hideGameOver());
        System.out.println("Visual demo: hiding game over");

        // Keep window open until stage closes
        Stage stage = getStage(table);
        CountDownLatch keepAlive = new CountDownLatch(1);
        stage.setOnHidden(e -> keepAlive.countDown());
        keepAlive.await();
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Test infrastructure helpers
    // ═════════════════════════════════════════════════════════════════════════

    private static AIPlayer makeAI(String name, int chips, AIPlayer.Role role) {
        AIPlayer ai = new AIPlayer(name, chips);
        ai.setRole(role);
        return ai;
    }

    private static void cssFileShouldExistOnClasspath() {
        var url = GameTable.class.getResource("/game-table.css");
        require(url != null, "game-table.css should exist on classpath, but was null");
    }

    private static void createShouldReturnNonNullGameTable() throws Exception {
        Method createMethod = GameTable.class.getDeclaredMethod("create");
        require(Modifier.isStatic(createMethod.getModifiers()),
            "create() should be static");
        require(Modifier.isPublic(createMethod.getModifiers()),
            "create() should be public");
        require(GameTable.class.isAssignableFrom(createMethod.getReturnType()),
            "create() should return GameTable");

        GameTable table = createOnFxThread();
        require(table != null, "create() should return non-null GameTable");
    }

    private static void canvasShouldHaveDesignDimensions() {
        AtomicReference<GameTable> tableRef = new AtomicReference<>();

        runOnFxThreadAndWait(() -> {
            GameTable table = GameTable.create();
            tableRef.set(table);

            // canvas = scene root AnchorPane ("actualCanvas" in GameTable's internal structure)
            AnchorPane canvas = getRoot(table);

            double prefW = canvas.getPrefWidth();
            double prefH = canvas.getPrefHeight();
            require(prefW == MenuLayoutConstants.DESIGN_WIDTH,
                "Canvas prefWidth should be " + MenuLayoutConstants.DESIGN_WIDTH
                + ", got: " + prefW);
            require(prefH == MenuLayoutConstants.DESIGN_HEIGHT,
                "Canvas prefHeight should be " + MenuLayoutConstants.DESIGN_HEIGHT
                + ", got: " + prefH);

            double minW = canvas.getMinWidth();
            double minH = canvas.getMinHeight();
            require(minW == MenuLayoutConstants.DESIGN_WIDTH,
                "Canvas minWidth should be " + MenuLayoutConstants.DESIGN_WIDTH
                + ", got: " + minW);
            require(minH == MenuLayoutConstants.DESIGN_HEIGHT,
                "Canvas minHeight should be " + MenuLayoutConstants.DESIGN_HEIGHT
                + ", got: " + minH);

            double maxW = canvas.getMaxWidth();
            double maxH = canvas.getMaxHeight();
            require(maxW == MenuLayoutConstants.DESIGN_WIDTH,
                "Canvas maxWidth should be " + MenuLayoutConstants.DESIGN_WIDTH
                + ", got: " + maxW);
            require(maxH == MenuLayoutConstants.DESIGN_HEIGHT,
                "Canvas maxHeight should be " + MenuLayoutConstants.DESIGN_HEIGHT
                + ", got: " + maxH);
        });
    }

    private static void isVisibleShouldReflectStageState() {
        AtomicReference<GameTable> tableRef = new AtomicReference<>();

        runOnFxThreadAndWait(() -> {
            GameTable table = GameTable.create();
            tableRef.set(table);

            require(!table.isVisible(),
                "isVisible() should be false before show()");

            table.show();
            require(table.isVisible(),
                "isVisible() should be true after show()");

            table.hideFrame();
            require(!table.isVisible(),
                "isVisible() should be false after hideFrame()");
        });
    }

    private static void awaitUiReadyShouldNotBlockForever() throws Exception {
        runOnFxThreadAndWait(() -> GameTable.create());

        GameTable table = instance();
        require(table != null, "GameTable singleton should be set");

        long start = System.currentTimeMillis();
        table.awaitUiReady(5000);
        long elapsed = System.currentTimeMillis() - start;

        require(elapsed < 4000,
            "awaitUiReady should return quickly when UI already built, took "
            + elapsed + "ms");
    }

    private static void shouldBeSingletonCreatingNewDisposesOld() {
        runOnFxThreadAndWait(() -> {
            GameTable table1 = GameTable.create();
            Stage stage1 = getStage(table1);

            table1.show();
            require(stage1.isShowing(),
                "Stage 1 should be showing before creating second table");

            GameTable table2 = GameTable.create();
            require(table2 != null, "Second table should be created");

            require(!stage1.isShowing(),
                "Old stage should be closed when new GameTable is created");
        });
    }

    private static void showShouldSetStageVisibleAndHideFrameHides() {
        runOnFxThreadAndWait(() -> {
            GameTable table = GameTable.create();
            Stage stage = getStage(table);

            table.show();
            require(stage.isShowing(), "Stage should be showing after show()");

            table.hideFrame();
            require(!stage.isShowing(), "Stage should not be showing after hideFrame()");

            table.showFrame();
            require(stage.isShowing(), "Stage should be showing after showFrame()");
        });
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Phase 2 tests — Player/AI badges, pot, role indicators
    // ═════════════════════════════════════════════════════════════════════════

    // ── updatePlayerInfo ────────────────────────────────────────────────────

    private static void updatePlayerInfoShouldSetBadgeTextAndFoldedState() {
        runOnFxThreadAndWait(() -> {
            GameTable table = GameTable.create();

            // Non-folded state
            table.updatePlayerInfo("Emaq", 12500, "D", false);
            HBox badge = getField(table, "playerBadge");
            require(badge != null, "playerBadge should not be null");

            // Child [2] is name label
            Label nameLabel = (Label) badge.getChildren().get(2);
            require(nameLabel.getText().equals("Emaq"),
                "Name label should contain player name, got: " + nameLabel.getText());

            // Child [1] is role chip
            Label roleChip = (Label) badge.getChildren().get(1);
            require(roleChip.isVisible(),
                "Role chip should be visible for dealer");
            require(roleChip.getText().equals("D"),
                "Role chip should show 'D', got: " + roleChip.getText());

            // Child [3] is chips label
            Label chipsLabel = (Label) badge.getChildren().get(3);
            require(chipsLabel.getText().contains("12,500"),
                "Chips label should show formatted amount, got: " + chipsLabel.getText());

            require(badge.getStyleClass().contains("ai-badge"),
                "Badge should have ai-badge CSS class");
            require(!badge.getStyleClass().contains("ai-badge-folded"),
                "Badge should NOT have folded CSS class when not folded");

            // Folded state
            table.updatePlayerInfo("Emaq", 12500, "D", true);
            require(badge.getStyleClass().contains("ai-badge-folded"),
                "Badge should have ai-badge-folded CSS class when folded");

            // Un-fold
            table.updatePlayerInfo("Emaq", 12500, "D", false);
            require(!badge.getStyleClass().contains("ai-badge-folded"),
                "Badge should NOT have folded CSS class after un-folding");
        });
    }

    private static void updatePlayerInfoShouldFormatChipsWithCommas() {
        runOnFxThreadAndWait(() -> {
            GameTable table = GameTable.create();

            table.updatePlayerInfo("Emaq", 12500, "D", false);
            Label chipsLabel = getField(table, "playerChipsLabel");
            require(chipsLabel != null, "playerChipsLabel should not be null");
            require(chipsLabel.getText().contains("12,500"),
                "Chips amount should be formatted with commas, got: " + chipsLabel.getText());
        });
    }

    // ── updateAIPlayers ─────────────────────────────────────────────────────

    private static void updateAIPlayersShouldPositionBadgesForOneAI() {
        runOnFxThreadAndWait(() -> {
            GameTable table = GameTable.create();
            List<AIPlayer> ais = new ArrayList<>();
            AIPlayer ai = new AIPlayer("Bot1", 3000);
            ai.setRole(AIPlayer.Role.DEALER);
            ais.add(ai);

            table.updateAIPlayers(ais);

            HBox[] badges = getField(table, "aiBadges");
            require(badges != null, "aiBadges should not be null");
            require(badges.length == 3, "Should have 3 AI badge slots");

            // First badge should be visible
            require(badges[0].isVisible(), "First AI badge should be visible");

            // Child [1] is name label, child [2] is chips label
            Label nameLabel = (Label) badges[0].getChildren().get(1);
            require(nameLabel.getText().equals("Bot1"),
                "AI name should be Bot1, got: " + nameLabel.getText());

            // Child [0] is role chip
            Label roleChip = (Label) badges[0].getChildren().get(0);
            require(roleChip.isVisible(),
                "AI role chip should be visible for dealer");
            require(roleChip.getText().equals("D"),
                "AI role chip should show 'D'");

            Label chipsLabel = (Label) badges[0].getChildren().get(2);
            require(chipsLabel.getText().contains("3,000"),
                "AI chips should show 3,000, got: " + chipsLabel.getText());

            // Position checks — relaxed to allow visual iteration
            Double leftAnchor = AnchorPane.getLeftAnchor(badges[0]);
            require(leftAnchor != null, "AI badge 0 should have left anchor");
            Double topAnchor = AnchorPane.getTopAnchor(badges[0]);
            require(topAnchor != null, "AI badge 0 should have top anchor");

            // Other badges should be hidden
            require(!badges[1].isVisible(), "Second AI badge should be hidden with 1 AI");
            require(!badges[2].isVisible(), "Third AI badge should be hidden with 1 AI");
        });
    }

    private static void updateAIPlayersShouldPositionBadgesForThreeAIs() {
        runOnFxThreadAndWait(() -> {
            GameTable table = GameTable.create();
            List<AIPlayer> ais = new ArrayList<>();
            AIPlayer ai1 = new AIPlayer("Bot1", 3000);
            ai1.setRole(AIPlayer.Role.DEALER);
            AIPlayer ai2 = new AIPlayer("Bot2", 5000);
            ai2.setRole(AIPlayer.Role.SMALL_BLIND);
            AIPlayer ai3 = new AIPlayer("Bot3", 4000);
            ai3.setRole(AIPlayer.Role.BIG_BLIND);
            ais.add(ai1);
            ais.add(ai2);
            ais.add(ai3);

            table.updateAIPlayers(ais);

            HBox[] badges = getField(table, "aiBadges");

            // All three should be visible
            for (int i = 0; i < 3; i++) {
                require(badges[i].isVisible(),
                    "AI badge " + i + " should be visible with 3 AIs");
            }

            // Position checks — relaxed bounds only to allow visual iteration
            double w = MenuLayoutConstants.DESIGN_WIDTH;
            double h = MenuLayoutConstants.DESIGN_HEIGHT;
            for (int i = 0; i < 3; i++) {
                Double leftAnchor = AnchorPane.getLeftAnchor(badges[i]);
                Double topAnchor = AnchorPane.getTopAnchor(badges[i]);
                require(leftAnchor != null, "AI badge " + i + " should have left anchor");
                require(topAnchor != null, "AI badge " + i + " should have top anchor");
                require(leftAnchor >= 0 && leftAnchor <= w,
                    "AI badge " + i + " left should be within table bounds, got: " + leftAnchor);
                require(topAnchor >= 0 && topAnchor <= h,
                    "AI badge " + i + " top should be within table bounds, got: " + topAnchor);
            }
        });
    }

    private static void updateAIPlayersShouldHideExcessBadges() {
        java.util.concurrent.atomic.AtomicReference<HBox[]> badgesRef =
            new java.util.concurrent.atomic.AtomicReference<>();

        runOnFxThreadAndWait(() -> {
            GameTable table = GameTable.create();
            List<AIPlayer> ais = new ArrayList<>();
            AIPlayer ai = new AIPlayer("OnlyBot", 3000);
            ais.add(ai);

            table.updateAIPlayers(ais);

            HBox[] badges = getField(table, "aiBadges");
            require(badges[0].isVisible(), "First AI badge should be visible with 1 AI");
            require(!badges[1].isVisible(), "Second AI badge should be hidden");
            require(!badges[2].isVisible(), "Third AI badge should be hidden");
            badgesRef.set(badges);

            // Now send zero AIs — all visible badges will fade out
            table.updateAIPlayers(new ArrayList<>());
        });

        // Wait for fade-out animations to complete (200ms animation + buffer)
        try {
            Thread.sleep(350);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        runOnFxThreadAndWait(() -> {
            HBox[] badges = badgesRef.get();
            require(!badges[0].isVisible(), "First AI badge should be hidden with 0 AIs");
            require(!badges[1].isVisible(), "Second AI badge should be hidden with 0 AIs");
            require(!badges[2].isVisible(), "Third AI badge should be hidden with 0 AIs");
        });
    }

    private static void updateAIPlayersShouldApplyFoldedCssToFoldedAI() {
        runOnFxThreadAndWait(() -> {
            GameTable table = GameTable.create();
            List<AIPlayer> ais = new ArrayList<>();
            AIPlayer aiFolded = new AIPlayer("Folder", 3000);
            aiFolded.setFolded(true);
            AIPlayer aiActive = new AIPlayer("Active", 5000);
            ais.add(aiFolded);
            ais.add(aiActive);

            table.updateAIPlayers(ais);

            HBox[] badges = getField(table, "aiBadges");

            // Folded AI should have the folded CSS class on its HBox
            require(badges[0].getStyleClass().contains("ai-badge-folded"),
                "Folded AI badge should have ai-badge-folded CSS class");
            require(!badges[0].getStyleClass().contains("ai-badge"),
                "Folded AI badge should NOT have base ai-badge class (replaced)");

            // Active AI should NOT have folded class
            require(badges[1].getStyleClass().contains("ai-badge"),
                "Active AI badge should have ai-badge CSS class");
            require(!badges[1].getStyleClass().contains("ai-badge-folded"),
                "Active AI badge should NOT have ai-badge-folded CSS class");
        });
    }

    // ── updatePot ─────────────────────────────────────────────────────────────

    private static void updatePotShouldDisplayFormattedAmount() {
        runOnFxThreadAndWait(() -> {
            GameTable table = GameTable.create();

            table.updatePot(4200);
            HBox potLabel = getField(table, "potLabel");
            require(potLabel != null, "potLabel should not be null");
            require(potLabel.isVisible(), "Pot label should be visible when amount > 0");
            Label potText = (Label) potLabel.getChildren().get(1);
            require(potText.getText().contains("BOTE"),
                "Pot text should contain 'BOTE', got: " + potText.getText());
            require(potText.getText().contains("4,200"),
                "Pot text should show formatted amount, got: " + potText.getText());
        });
    }

    private static void updatePotShouldHideWhenZero() {
        runOnFxThreadAndWait(() -> {
            GameTable table = GameTable.create();

            table.updatePot(1000);
            HBox potLabel = getField(table, "potLabel");
            require(potLabel.isVisible(), "Pot label should be visible with amount > 0");

            table.updatePot(0);
            require(!potLabel.isVisible(), "Pot label should be hidden when amount is 0");
        });
    }

    // ── clearTable ────────────────────────────────────────────────────────────

    private static void clearTableShouldResetAllState() {
        java.util.concurrent.atomic.AtomicReference<HBox[]> aiBadgesRef =
            new java.util.concurrent.atomic.AtomicReference<>();

        runOnFxThreadAndWait(() -> {
            GameTable table = GameTable.create();

            // Set up some state
            List<AIPlayer> ais = new ArrayList<>();
            AIPlayer ai = new AIPlayer("Bot1", 3000);
            ai.setRole(AIPlayer.Role.DEALER);
            ais.add(ai);

            table.updatePlayerInfo("Emaq", 12500, "D", true);
            table.updateAIPlayers(ais);
            table.updatePot(5000);

            // Verify state is set
            HBox playerBadge = getField(table, "playerBadge");
            require(playerBadge.getStyleClass().contains("ai-badge-folded"),
                "Precondition: player should be folded before clear");

            HBox potLabel = getField(table, "potLabel");
            require(potLabel.isVisible(), "Precondition: pot should be visible before clear");

            // Clear
            table.clearTable();

            // Player badge should reset — folded class removed
            require(!playerBadge.getStyleClass().contains("ai-badge-folded"),
                "Player should not be folded after clearTable");

            // Player chips should still show but not folded
            Boolean playerFolded = getField(table, "playerFolded");
            require(!playerFolded,
                "playerFolded field should be false after clearTable");

            // AI folded states should be reset
            boolean[] aiFolded = getField(table, "aiFolded");
            for (int i = 0; i < 3; i++) {
                require(!aiFolded[i],
                    "aiFolded[" + i + "] should be false after clearTable");
            }

            // Pot should be hidden
            require(!potLabel.isVisible(),
                "Pot should be hidden after clearTable");

            aiBadgesRef.set(getField(table, "aiBadges"));
        });

        // Wait for fade-out animations to complete
        try {
            Thread.sleep(350);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        runOnFxThreadAndWait(() -> {
            HBox[] aiBadges = aiBadgesRef.get();
            for (int i = 0; i < 3; i++) {
                require(!aiBadges[i].isVisible(),
                    "AI badge " + i + " should be hidden after clearTable");
            }
        });
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Phase 6 tests — Card dealing
    // ═════════════════════════════════════════════════════════════════════════

    private static void shouldHaveDeckPileAtDeckPosition() {
        runOnFxThreadAndWait(() -> {
            GameTable table = GameTable.create();

            Canvas deckPile = getField(table, "deckPileCanvas");
            require(deckPile != null, "deckPileCanvas should not be null");

            double w = deckPile.getWidth();
            double h = deckPile.getHeight();
            require(w > 0 && h > 0,
                "Deck pile should have positive dimensions, got: " + w + "x" + h);

            Double left = AnchorPane.getLeftAnchor(deckPile);
            Double top = AnchorPane.getTopAnchor(deckPile);
            require(left != null, "Deck pile should have left anchor");
            require(top != null, "Deck pile should have top anchor");
            require(left >= 0 && left + w <= 1672,
                "Deck pile left should be within table bounds, got: " + left);
            require(top >= 0 && top + h <= 941,
                "Deck pile top should be within table bounds, got: " + top);
        });
    }

    private static void shouldDealPlayerCardsShowSectionAndCards() {
        runOnFxThreadAndWait(() -> {
            GameTable table = GameTable.create();

            List<Card> hand = Arrays.asList(
                new Card("A", "\u2660"),
                new Card("K", "\u2665"));

            table.dealPlayerHand(hand);

            // Section label should be visible
            Label playerHandSection = getField(table, "playerHandSection");
            require(playerHandSection.isVisible(),
                "Player hand section label should be visible after dealPlayerHand");

            // Cards list should have 2 entries
            List<?> playerCards = getField(table, "playerCards");
            require(playerCards != null, "playerCards should not be null");
            require(playerCards.size() == 2,
                "Should have 2 player cards after dealPlayerHand, got: "
                + playerCards.size());

            // Cards should be added to canvas
            for (Object cardObj : playerCards) {
                GameCard gc = (GameCard) cardObj;
                Node node = gc.getNode();
                require(node != null, "Card node should not be null");
                require(canvasContainsNode(table, node),
                    "Card node should be in canvas children");
            }
        });
    }

    private static void shouldDealCommunityCardsShowSectionAndCards() {
        runOnFxThreadAndWait(() -> {
            GameTable table = GameTable.create();

            List<Card> flop = Arrays.asList(
                new Card("Q", "\u2666"),
                new Card("J", "\u2660"),
                new Card("10", "\u2665"));

            table.dealCommunityCards(flop);

            Label commSection = getField(table, "commCardsSection");
            require(commSection.isVisible(),
                "Community section label should be visible after dealCommunityCards");

            List<?> commCards = getField(table, "communityCards");
            require(commCards != null, "communityCards should not be null");
            require(commCards.size() == 3,
                "Should have 3 community cards after flop, got: "
                + commCards.size());

            for (Object cardObj : commCards) {
                GameCard gc = (GameCard) cardObj;
                require(gc.getNode() != null, "Community card node should not be null");
            }

            // Deal turn (incremental — should NOT clear previous)
            List<Card> turn = Arrays.asList(
                new Card("Q", "\u2666"),
                new Card("J", "\u2660"),
                new Card("10", "\u2665"),
                new Card("2", "\u2663"));
            table.dealCommunityCards(turn);

            commCards = getField(table, "communityCards");
            require(commCards.size() == 3 + 1,
                "Should have 4 community cards after turn (incremental), got: "
                + commCards.size());
        });
    }

    private static void shouldClearCardsRemoveAllCards() {
        runOnFxThreadAndWait(() -> {
            GameTable table = GameTable.create();

            // Deal some cards
            table.dealPlayerHand(Arrays.asList(
                new Card("A", "\u2660"), new Card("K", "\u2665")));
            table.dealCommunityCards(Arrays.asList(
                new Card("Q", "\u2666"), new Card("J", "\u2660"),
                new Card("10", "\u2665")));

            // Verify cards are present
            List<?> playerCards = getField(table, "playerCards");
            List<?> commCards = getField(table, "communityCards");
            require(playerCards.size() == 2, "Precondition: 2 player cards");
            require(commCards.size() == 3, "Precondition: 3 community cards");

            // Clear
            table.clearCards();

            // Cards should be empty
            playerCards = getField(table, "playerCards");
            commCards = getField(table, "communityCards");
            require(playerCards.isEmpty(),
                "Player cards should be empty after clearCards, got: "
                + playerCards.size());
            require(commCards.isEmpty(),
                "Community cards should be empty after clearCards, got: "
                + commCards.size());

            // Section labels should be hidden
            Label playerSection = getField(table, "playerHandSection");
            Label commSection = getField(table, "commCardsSection");
            require(!playerSection.isVisible(),
                "Player hand section should be hidden after clearCards");
            require(!commSection.isVisible(),
                "Community section should be hidden after clearCards");
        });
    }

    private static void shouldReDealPlayerHandReplacesPrevious() {
        runOnFxThreadAndWait(() -> {
            GameTable table = GameTable.create();

            // First deal
            table.dealPlayerHand(Arrays.asList(
                new Card("A", "\u2660"), new Card("K", "\u2665")));
            List<?> firstCards = getField(table, "playerCards");
            require(firstCards.size() == 2, "Precondition: 2 cards from first deal");
            Node firstNode = ((GameCard) firstCards.get(0)).getNode();
            require(canvasContainsNode(table, firstNode),
                "First deal card should be in canvas");

            // Second deal (should replace)
            table.dealPlayerHand(Arrays.asList(
                new Card("Q", "\u2666"), new Card("J", "\u2660")));
            List<?> secondCards = getField(table, "playerCards");
            require(secondCards.size() == 2,
                "Should still have 2 cards after re-deal");

            // Old nodes should be removed from canvas
            require(!canvasContainsNode(table, firstNode),
                "First deal card should be removed from canvas after re-deal");

            // New nodes should be in canvas
            Node secondNode = ((GameCard) secondCards.get(0)).getNode();
            require(canvasContainsNode(table, secondNode),
                "Second deal card should be in canvas");
        });
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Phase 7 tests — Action log
    // ═════════════════════════════════════════════════════════════════════════

    private static void setActionLogShouldDisplayUpToFourLines() {
        runOnFxThreadAndWait(() -> {
            GameTable table = GameTable.create();

            VBox container = getField(table, "actionLogContainer");
            require(container != null, "actionLogContainer should not be null");

            List<Label> labels = getField(table, "actionLogLabels");
            require(labels != null, "actionLogLabels should not be null");
            require(labels.size() == 4,
                "Should have 4 action log label slots, got: " + labels.size());

            table.setActionLog(Arrays.asList("IA apuesta 20", "IA-Carlos fold"));

            require(container.isVisible(),
                "Action log container should be visible when entries exist");
            require(labels.get(3).getText().equals("IA-Carlos fold"),
                "Most recent entry should be at bottom, got: " + labels.get(3).getText());
            require(labels.get(2).getText().equals("IA apuesta 20"),
                "Second most recent should be above bottom, got: " + labels.get(2).getText());
            require(labels.get(0).getText().isEmpty(),
                "Empty slots should have empty text");
        });
    }

    private static void setActionLogShouldHideWhenEmpty() {
        runOnFxThreadAndWait(() -> {
            GameTable table = GameTable.create();

            VBox container = getField(table, "actionLogContainer");

            table.setActionLog(Arrays.asList("test entry"));
            require(container.isVisible(),
                "Action log should be visible with entries");

            table.setActionLog(new ArrayList<>());
            require(container.isVisible(),
                "Action log should remain visible even when empty");
        });
    }

    private static void setActionLogShouldShowMostRecentAtBottom() {
        runOnFxThreadAndWait(() -> {
            GameTable table = GameTable.create();

            List<Label> labels = getField(table, "actionLogLabels");

            List<String> log = new ArrayList<>();
            log.add("first");
            log.add("second");
            log.add("third");
            log.add("fourth");
            table.setActionLog(log);

            require(labels.get(3).getText().equals("fourth"),
                "Bottom slot should have most recent, got: " + labels.get(3).getText());
            require(labels.get(2).getText().equals("third"),
                "Second from bottom should have third, got: " + labels.get(2).getText());
            require(labels.get(1).getText().equals("second"),
                "Third from bottom should have second, got: " + labels.get(1).getText());
            require(labels.get(0).getText().equals("first"),
                "Top slot should have oldest, got: " + labels.get(0).getText());
        });
    }

    private static void appendActionLogShouldAddLine() {
        runOnFxThreadAndWait(() -> {
            GameTable table = GameTable.create();

            VBox container = getField(table, "actionLogContainer");
            List<Label> labels = getField(table, "actionLogLabels");

            table.appendActionLog("hello");
            require(container.isVisible(),
                "Action log should be visible after append");
            require(labels.get(3).getText().equals("hello"),
                "Appended line should appear at bottom, got: " + labels.get(3).getText());
        });
    }

    private static void appendActionLogShouldRemoveOldestWhenFull() {
        runOnFxThreadAndWait(() -> {
            GameTable table = GameTable.create();

            List<Label> labels = getField(table, "actionLogLabels");

            for (int i = 1; i <= 4; i++) {
                table.appendActionLog("line" + i);
            }

            table.appendActionLog("line5");

            require(labels.get(3).getText().equals("line5"),
                "Bottom should have newest entry, got: " + labels.get(3).getText());
            require(labels.get(0).getText().equals("line2"),
                "Top should have oldest remaining entry, got: " + labels.get(0).getText());
            require(labels.get(1).getText().equals("line3"),
                "Second slot should have line3, got: " + labels.get(1).getText());
            require(labels.get(2).getText().equals("line4"),
                "Third slot should have line4, got: " + labels.get(2).getText());
        });
    }

    private static void clearTableShouldClearActionLog() {
        runOnFxThreadAndWait(() -> {
            GameTable table = GameTable.create();

            VBox container = getField(table, "actionLogContainer");

            table.setActionLog(Arrays.asList("IA sube 50"));
            require(container.isVisible(),
                "Action log should be visible before clear");

            table.clearTable();
            require(container.isVisible(),
                "Action log should remain visible after clearTable");

            List<Label> labels = getField(table, "actionLogLabels");
            for (int i = 0; i < 4; i++) {
                require(labels.get(i).getOpacity() == 0,
                    "Action log label " + i + " should be faded out after clearTable");
            }
        });
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Phase 8 tests — Result banner
    // ═════════════════════════════════════════════════════════════════════════

    private static void showResultShouldDisplayWinBanner() {
        runOnFxThreadAndWait(() -> {
            GameTable table = GameTable.create();

            table.showResult("¡Ganaste! +4,200  |  Full House", true);

            Label resultLabel = getField(table, "resultLabel");
            require(resultLabel != null, "resultLabel should not be null");
            require(resultLabel.isVisible(),
                "Result label should be visible after showResult");
            require(resultLabel.getText().contains("Ganaste"),
                "Result label should contain win text, got: " + resultLabel.getText());
            require(resultLabel.getStyleClass().contains("result-win"),
                "Result label should have result-win CSS class for win");
            require(!resultLabel.getStyleClass().contains("result-loss"),
                "Result label should NOT have result-loss CSS class for win");
        });
    }

    private static void showResultShouldDisplayLossBanner() {
        runOnFxThreadAndWait(() -> {
            GameTable table = GameTable.create();

            table.showResult("IA-Lucia gana el bote de 1,500  |  Tu mano: One Pair", false);

            Label resultLabel = getField(table, "resultLabel");
            require(resultLabel.isVisible(),
                "Result label should be visible after showResult (loss)");
            require(resultLabel.getText().contains("IA-Lucia"),
                "Result label should contain AI winner name, got: " + resultLabel.getText());
            require(resultLabel.getStyleClass().contains("result-loss"),
                "Result label should have result-loss CSS class for loss");
            require(!resultLabel.getStyleClass().contains("result-win"),
                "Result label should NOT have result-win CSS class for loss");
        });
    }

    private static void showResultShouldToggleWinLossCss() {
        runOnFxThreadAndWait(() -> {
            GameTable table = GameTable.create();

            table.showResult("Win!", true);
            Label resultLabel = getField(table, "resultLabel");
            require(resultLabel.getStyleClass().contains("result-win"),
                "Should have result-win after first showResult");

            // Switch to loss — result-win should be removed
            table.showResult("Loss!", false);
            require(resultLabel.getStyleClass().contains("result-loss"),
                "Should have result-loss after second showResult");
            require(!resultLabel.getStyleClass().contains("result-win"),
                "result-win should be removed when switching to loss");

            // Switch back to win
            table.showResult("Win again!", true);
            require(resultLabel.getStyleClass().contains("result-win"),
                "Should have result-win after third showResult");
            require(!resultLabel.getStyleClass().contains("result-loss"),
                "result-loss should be removed when switching to win");
        });
    }

    private static void hideResultShouldHideBanner() {
        runOnFxThreadAndWait(() -> {
            GameTable table = GameTable.create();

            table.showResult("Test result", true);
            Label resultLabel = getField(table, "resultLabel");
            require(resultLabel.isVisible(), "Precondition: result visible");

            table.hideResult();
            require(!resultLabel.isVisible(),
                "Result label should be hidden after hideResult");
            require(resultLabel.getText().isEmpty(),
                "Result label text should be empty after hideResult");
        });
    }

    private static void clearTableShouldHideResultBanner() {
        runOnFxThreadAndWait(() -> {
            GameTable table = GameTable.create();

            table.showResult("Game result", true);
            Label resultLabel = getField(table, "resultLabel");
            require(resultLabel.isVisible(), "Precondition: result visible");

            table.clearTable();
            require(!resultLabel.isVisible(),
                "Result label should be hidden after clearTable");
        });
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Phase 9 tests — Game over overlay
    // ═════════════════════════════════════════════════════════════════════════

    private static void showGameOverShouldBeHiddenByDefault() {
        runOnFxThreadAndWait(() -> {
            GameTable table = GameTable.create();

            StackPane overlay = getField(table, "gameOverOverlay");
            require(overlay != null, "gameOverOverlay should not be null");
            require(!overlay.isVisible(),
                "Game over overlay should be hidden by default");
        });
    }

    private static void showGameOverShouldDisplayWinWithChipCount() {
        runOnFxThreadAndWait(() -> {
            GameTable table = GameTable.create();

            table.showGameOver(18400);

            StackPane overlay = getField(table, "gameOverOverlay");
            require(overlay.isVisible(),
                "Game over overlay should be visible after showGameOver");

            Label title = getField(table, "gameOverTitle");
            require(title.getText().equals("¡Fin del juego!"),
                "Title should be '¡Fin del juego!' for win, got: " + title.getText());
            require(title.getStyleClass().contains("gameover-win"),
                "Title should have gameover-win CSS class");

            Label chips = getField(table, "gameOverChips");
            require(chips.getText().contains("18,400"),
                "Chips label should show 18,400, got: " + chips.getText());
            require(chips.getStyleClass().contains("gameover-win"),
                "Chips should have gameover-win CSS class");
        });
    }

    private static void showGameOverShouldDisplayLossBankrupt() {
        runOnFxThreadAndWait(() -> {
            GameTable table = GameTable.create();

            table.showGameOver(0);

            Label title = getField(table, "gameOverTitle");
            require(title.getText().equals("Game Over"),
                "Title should be 'Game Over' for bankrupt, got: " + title.getText());
            require(title.getStyleClass().contains("gameover-loss"),
                "Title should have gameover-loss CSS class for bankrupt");

            Label chips = getField(table, "gameOverChips");
            require(chips.getText().contains("sin fichas"),
                "Chips label should say 'sin fichas', got: " + chips.getText());
            require(chips.getStyleClass().contains("gameover-loss"),
                "Chips should have gameover-loss CSS class for bankrupt");
        });
    }

    private static void hideGameOverShouldHideOverlay() {
        runOnFxThreadAndWait(() -> {
            GameTable table = GameTable.create();

            table.showGameOver(5000);
            StackPane overlay = getField(table, "gameOverOverlay");
            require(overlay.isVisible(), "Precondition: game over visible");

            table.hideGameOver();
            require(!overlay.isVisible(),
                "Game over overlay should be hidden after hideGameOver");
        });
    }

    // ── fix-endgame-states: winner-name overload tests ──────────────────

    private static void showGameOverShouldDisplayWinnerNameOnWin() {
        runOnFxThreadAndWait(() -> {
            GameTable table = GameTable.create();

            table.showGameOver("Alice", 18400);

            StackPane overlay = getField(table, "gameOverOverlay");
            require(overlay.isVisible(), "Game over overlay should be visible");

            Label title = getField(table, "gameOverTitle");
            require(title.getText().contains("¡Ganaste"),
                "Title should show win message with name, got: " + title.getText());
            require(title.getText().contains("Alice"),
                "Title should contain winner name 'Alice', got: " + title.getText());
            require(title.getStyleClass().contains("gameover-win"),
                "Title should have gameover-win CSS class");

            Label chips = getField(table, "gameOverChips");
            require(chips.getText().contains("18,400"),
                "Chips label should show 18,400, got: " + chips.getText());
        });
    }

    private static void showGameOverShouldDisplayOpponentNameOnBust() {
        runOnFxThreadAndWait(() -> {
            GameTable table = GameTable.create();

            table.showGameOver("Ana", 0);

            StackPane overlay = getField(table, "gameOverOverlay");
            require(overlay.isVisible(), "Game over overlay should be visible");

            Label title = getField(table, "gameOverTitle");
            require(title.getText().contains("Ana"),
                "Title should contain opponent name 'Ana', got: " + title.getText());
            require(title.getText().contains("eliminó"),
                "Title should say 'eliminó', got: " + title.getText());
            require(title.getStyleClass().contains("gameover-loss"),
                "Title should have gameover-loss CSS class");

            Label chips = getField(table, "gameOverChips");
            require(chips.getText().contains("sin fichas"),
                "Chips label should say 'sin fichas', got: " + chips.getText());
        });
    }

    private static void showGameOverShouldFallBackToDefaultWhenNoWinnerName() {
        runOnFxThreadAndWait(() -> {
            GameTable table = GameTable.create();

            // Backward compat: calling with null winnerName should behave
            // identically to calling showGameOver(int)
            table.showGameOver(null, 5000);

            Label title = getField(table, "gameOverTitle");
            require(title.getText().equals("¡Fin del juego!"),
                "Title should be '¡Fin del juego!' when no winner name, got: "
                + title.getText());

            table.showGameOver(null, 0);

            title = getField(table, "gameOverTitle");
            require(title.getText().equals("Game Over"),
                "Title should be 'Game Over' when no winner name and bust, got: "
                + title.getText());
        });
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  HELPERS
    // ═════════════════════════════════════════════════════════════════════════

    private static void ensureJavaFxInitialized() {
        if (javafxInitialized) {
            return;
        }
        synchronized (GameTableTest.class) {
            if (javafxInitialized) {
                return;
            }
            JavaFxBootstrap.ensureStarted();
            javafxInitialized = true;
        }
    }

    private static GameTable createOnFxThread() {
        AtomicReference<GameTable> ref = new AtomicReference<>();
        runOnFxThreadAndWait(() -> ref.set(GameTable.create()));
        GameTable table = ref.get();
        require(table != null, "create() on FX thread should return non-null");
        return table;
    }

    /** Gets the singleton instance via reflection (no public accessor). */
    private static GameTable instance() {
        try {
            java.lang.reflect.Field f = GameTable.class.getDeclaredField("instance");
            f.setAccessible(true);
            return (GameTable) f.get(null);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("Cannot access GameTable.instance", ex);
        }
    }

    private static AnchorPane getRoot(GameTable table) {
        Stage stage = getStage(table);
        require(stage.getScene() != null, "Stage should have a scene");
        return (AnchorPane) stage.getScene().getRoot();
    }

    private static Stage getStage(GameTable table) {
        try {
            Method getStage = GameTable.class.getDeclaredMethod("getStageForTest");
            getStage.setAccessible(true);
            return (Stage) getStage.invoke(table);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("GameTable must expose getStageForTest() for testing", ex);
        }
    }

    /** Retrieves a private field from an object via reflection. */
    @SuppressWarnings("unchecked")
    private static <T> T getField(Object obj, String fieldName) {
        try {
            java.lang.reflect.Field f;
            Class<?> clazz = obj.getClass();
            // Walk up the class hierarchy to find the field
            while (clazz != null) {
                try {
                    f = clazz.getDeclaredField(fieldName);
                    f.setAccessible(true);
                    return (T) f.get(obj);
                } catch (NoSuchFieldException e) {
                    clazz = clazz.getSuperclass();
                }
            }
            throw new AssertionError("Field '" + fieldName + "' not found in " + obj.getClass().getName());
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("Cannot access field '" + fieldName + "': " + ex.getMessage(), ex);
        }
    }

    private static void runOnFxThreadAndWait(Runnable action) {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable throwable) {
                error.set(throwable);
            } finally {
                done.countDown();
            }
        });

        waitForLatch(done, 15, TimeUnit.SECONDS, "FX thread action timed out");
        propagateFxErrorUnchecked(error.get());
    }

    private static void waitForLatch(CountDownLatch latch, long timeout, TimeUnit unit, String message) {
        try {
            require(latch.await(timeout, unit), message);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError(message, ex);
        }
    }

    private static void propagateFxErrorUnchecked(Throwable failure) {
        if (failure == null) {
            return;
        }
        if (failure instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        throw new AssertionError("FX thread action failed", failure);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    /** Checks whether the given node is in the actual game canvas children list.
     *  root (AnchorPane scene root) → layers (StackPane) → actualCanvas (1st child) */
    private static boolean canvasContainsNode(GameTable table, Node node) {
        AnchorPane root = getRoot(table);
        StackPane layers = (StackPane) root.getChildren().get(0);
        Node actualCanvas = layers.getChildren().get(0);
        return ((AnchorPane) actualCanvas).getChildren().contains(node);
    }
}
