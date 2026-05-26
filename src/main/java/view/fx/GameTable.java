package view.fx;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import config.GameSettings;
import javafx.animation.FadeTransition;
import javafx.animation.RotateTransition;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.AnchorPane;
import javafx.geometry.Pos;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import model.AIPlayer;
import model.BettingRound;
import model.Card;

/**
 * GameTable — JavaFX replacement for Swing {@code GameView} JFrame.
 *
 * Phase 1: Empty shell with background image, title bar, and visibility API.
 * Phase 2: Player/AI badges, pot display, role indicators (CSS-styled Labels).
 * Phase 3: Redesigned UI with design-system tokens — chip icon box, table title,
 *          fade transitions, section labels, player name label.
 * Phase 4: Enhanced CSS with glass-morphism feel, 3D chip icon depth,
 *          fold/diamond/hexagon indicator icons, HBox badge wrappers.
 * Phase 5: Redesigned solid badges with poker-chip role indicators,
 *          chip rotation animations on role change.
 * Singleton-ish: creating a new instance disposes the previous one.
 *
 * Thread safety: {@link #show()} delegates to {@link Platform#runLater(Runnable)}
 * when called from a non-FX thread. All public update methods follow the same pattern.
 * {@link #awaitUiReady(long)} blocks until the UI latch is released.
 */
public final class GameTable {

    private static final String BG_PATH = "/game-bg.png";
    private static final String FONTS_CSS_PATH = "/fonts.css";
    private static final String CSS_PATH = "/game-table.css";
    private static final String CHROME_CSS_PATH = "/menu-chrome.css";

    private static final double DISPLAY_W = MenuLayoutConstants.DESIGN_WIDTH;
    private static final double DISPLAY_H = MenuLayoutConstants.DESIGN_HEIGHT;
    private static final Duration ACTION_LOG_FADE_DURATION = Duration.millis(
        220
    );

    // ── Singleton instance ────────────────────────────────────────────────────
    private static volatile GameTable instance;

    // ── UI components ─────────────────────────────────────────────────────────
    private Stage stage;
    private CountDownLatch uiReadyLatch;
    private CountDownLatch closeLatch;
    private AnchorPane canvas;
    private AnchorPane actualCanvas;

    // ── Player info ───────────────────────────────────────────────────────────
    private HBox playerBadge;
    private Label foldIndicator;
    private Label playerRoleChip;
    private Label playerBadgeLabel;
    private Label playerChipsLabel;
    private String playerPrevRole = "";
    private boolean playerFolded;

    // ── AI player badges (max 3) ──────────────────────────────────────────────
    private HBox[] aiBadges = new HBox[3];
    private Label[] aiRoleChips = new Label[3];
    private Label[] aiBadgeLabels = new Label[3];
    private Label[] aiChipsLabels = new Label[3];
    private boolean[] aiFolded = new boolean[3];
    private String[] aiPrevRoles = new String[] { "", "", "" };
    private FadeTransition[] aiFades = new FadeTransition[3];

    // ── Pot ───────────────────────────────────────────────────────────────────
    private HBox potLabel;
    private Label potIconLabel;
    private Label potTextLabel;

    // ── Decorative labels ─────────────────────────────────────────────────────
    private Label tableTitleLabel;
    private Label commCardsSection;
    private Label playerHandSection;
    private Label playerNameLabel;

    // ── Card dealing ──────────────────────────────────────────────────────────
    private List<GameCard> playerCards = new ArrayList<>();
    private List<GameCard> communityCards = new ArrayList<>();
    private boolean playerCardsDealt;
    private javafx.scene.canvas.Canvas deckPileCanvas;

    // ── Result banner ────────────────────────────────────────────────────────────
    private Label resultLabel;

    // ── Game over overlay ───────────────────────────────────────────────────────
    private StackPane gameOverOverlay;
    private Label gameOverTitle;
    private Label gameOverChips;

    // ── Action log ─────────────────────────────────────────────────────────────
    private VBox actionLogContainer;
    private final List<Label> actionLogLabels = new ArrayList<>();
    private static final int ACTION_LOG_MAX_LINES = 4;

    // ── Betting buttons ───────────────────────────────────────────────────────
    private HBox bettingPanel;

    // ── Status bar ────────────────────────────────────────────────────────────
    private HBox statusBar;
    private Label statusDot;
    private Label statusPhase;
    private Label statusTurn;

    // ── Exit flow ────────────────────────────────────────────────────────────
    private Runnable onExitConfirmed;
    private volatile boolean exitDialogShowing;

    // ── Game-flow synchronisation (blocking from non-FX threads) ──────────────
    private volatile CompletableFuture<Void> lastAnimationFuture;
    private volatile CompletableFuture<BettingRound.Action> pendingActionFuture;

    // ── Latest player name / chips for bridge methods ────────────────────────
    private String latestPlayerName = "";
    private int latestPlayerChips;

    /**
     * Returns the singleton instance. If an instance already exists,
     * it is disposed first (stage closed, latch released).
     */
    public static GameTable create() {
        if (instance != null) {
            if (Platform.isFxApplicationThread()) {
                instance.dispose();
            } else {
                Platform.runLater(instance::dispose);
            }
        }
        GameTable table = new GameTable();
        instance = table;
        return table;
    }

    private GameTable() {
        this.uiReadyLatch = new CountDownLatch(1);
        this.closeLatch = new CountDownLatch(1);
        if (Platform.isFxApplicationThread()) {
            buildStage();
        } else {
            Platform.runLater(this::buildStage);
        }
    }

    // ── Build ─────────────────────────────────────────────────────────────────

    private void buildStage() {
        stage = new Stage(StageStyle.UNDECORATED);
        stage.initModality(Modality.NONE);
        stage.setTitle("Royal Poker");

        canvas = new AnchorPane();
        canvas.setStyle("-fx-background-color: black;");
        canvas.setPrefSize(DISPLAY_W, DISPLAY_H);
        canvas.setMinSize(DISPLAY_W, DISPLAY_H);
        canvas.setMaxSize(DISPLAY_W, DISPLAY_H);

        StackPane layers = new StackPane();
        layers.setPrefSize(DISPLAY_W, DISPLAY_H);
        layers.setMinSize(DISPLAY_W, DISPLAY_H);
        layers.setMaxSize(DISPLAY_W, DISPLAY_H);

        actualCanvas = new AnchorPane();
        actualCanvas.getStyleClass().add("game-table-canvas");
        actualCanvas.setPrefSize(DISPLAY_W, DISPLAY_H);
        actualCanvas.setMinSize(DISPLAY_W, DISPLAY_H);
        actualCanvas.setMaxSize(DISPLAY_W, DISPLAY_H);

        gameOverOverlay = new StackPane();
        gameOverOverlay.setVisible(false);
        gameOverOverlay.setPrefSize(DISPLAY_W, DISPLAY_H);

        Rectangle backdrop = new Rectangle(DISPLAY_W, DISPLAY_H);
        backdrop.setFill(Color.rgb(0, 0, 0, 0.65));

        VBox panel = new VBox(16);
        panel.getStyleClass().add("gameover-panel");
        panel.setPrefSize(440, 220);
        panel.setMaxWidth(440);
        panel.setMaxHeight(220);
        panel.setAlignment(Pos.CENTER);

        applyRoundedClip(panel, 28);

        gameOverTitle = new Label();
        gameOverTitle.getStyleClass().add("gameover-title");

        gameOverChips = new Label();
        gameOverChips.getStyleClass().add("gameover-chips");

        panel.getChildren().addAll(gameOverTitle, gameOverChips);
        gameOverOverlay.getChildren().addAll(backdrop, panel);

        layers.getChildren().addAll(actualCanvas, gameOverOverlay);
        canvas.getChildren().add(layers);

        // Move the chrome title bar from actualCanvas to canvas (scene root)
        // so it always renders on TOP of the game over overlay.
        // Chrome was added as the first child of actualCanvas by FxMenuChrome.apply.
        if (!actualCanvas.getChildren().isEmpty()) {
            javafx.scene.Node chromeBar = actualCanvas.getChildren().get(0);
            actualCanvas.getChildren().remove(0);
            AnchorPane.setTopAnchor(chromeBar, 0.0);
            AnchorPane.setLeftAnchor(chromeBar, 0.0);
            AnchorPane.setRightAnchor(chromeBar, 0.0);
            canvas.getChildren().add(chromeBar);
        }

        ImageView bg = MenuImages.fullCanvasLayer(BG_PATH);
        bg.setFitWidth(DISPLAY_W);
        bg.setFitHeight(DISPLAY_H);
        AnchorPane.setTopAnchor(bg, 0.0);
        AnchorPane.setLeftAnchor(bg, 0.0);
        actualCanvas.getChildren().add(bg);

        FxMenuChrome.apply(stage, actualCanvas, "Royal Poker");

        buildComponents(actualCanvas);

        Scene scene = new Scene(canvas, DISPLAY_W, DISPLAY_H, Color.BLACK);
        loadStyles(scene);

        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                requestExit();
            }
        });

        stage.setScene(scene);

        stage.setOnCloseRequest(event -> {
            event.consume();
            requestExit();
        });

        uiReadyLatch.countDown();
    }

    private void loadStyles(Scene scene) {
        var fontsCss = GameTable.class.getResource(FONTS_CSS_PATH);
        if (fontsCss != null) scene
            .getStylesheets()
            .add(fontsCss.toExternalForm());
        var css = GameTable.class.getResource(CSS_PATH);
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        var chromeCss = GameTable.class.getResource(CHROME_CSS_PATH);
        if (chromeCss != null) {
            scene.getStylesheets().add(chromeCss.toExternalForm());
        }
    }

    /**
     * Creates and positions all game-info labels on the canvas.
     * Called once during {@link #buildStage()}.
     *
     * Badge structure (player): [foldIndicator] [roleChip] [name] [chips]
     * Badge structure (AI):     [roleChip] [name] [chips]
     */
    private void buildComponents(AnchorPane canvas) {
        // ── Table title (top-center) ──────────────────────────────────────
        // Table title hidden for now
        // tableTitleLabel = new Label("\u2660  ROYAL POKER  \u2660");
        // tableTitleLabel.getStyleClass().add("table-title");
        // AnchorPane.setLeftAnchor(tableTitleLabel, DISPLAY_W / 2 - 300.0);
        // AnchorPane.setTopAnchor(tableTitleLabel, 10.0);
        // canvas.getChildren().add(tableTitleLabel);

        // ── Player name label (bottom-left) ───────────────────────────────
        // REMOVED: playerNameLabel — playerBadge now serves as the name display

        // ── Player badge HBox (bottom-left) ────────────────────────────────
        //  Children: [0]=foldIndicator ✕  [1]=roleChip  [2]=name  [3]=chips
        playerBadge = new HBox();
        playerBadge.getStyleClass().add("player-badge");

        foldIndicator = new Label("\u2715");
        foldIndicator.getStyleClass().add("fold-indicator");
        foldIndicator.setVisible(false);

        playerRoleChip = new Label("");
        playerRoleChip.getStyleClass().add("chip-purple");
        playerRoleChip.setVisible(false);

        playerBadgeLabel = new Label();
        playerBadgeLabel.getStyleClass().add("badge-name");

        playerChipsLabel = new Label();
        playerChipsLabel.getStyleClass().add("badge-chips");

        playerBadge
            .getChildren()
            .addAll(
                foldIndicator,
                playerRoleChip,
                playerBadgeLabel,
                playerChipsLabel
            );
        applyRoundedClip(playerBadge, 28);

        AnchorPane.setLeftAnchor(playerBadge, 55.0);
        AnchorPane.setTopAnchor(playerBadge, DISPLAY_H - 95.0);
        canvas.getChildren().add(playerBadge);

        // ── AI badges (top row, 3 positions) ──────────────────────────────
        //  Each HBox: [0]=roleChip  [1]=name  [2]=chips
        double[] aiX = { 60.0, DISPLAY_W / 2 - 80.0, DISPLAY_W - 160.0 };
        for (int i = 0; i < 3; i++) {
            HBox aiBox = new HBox();
            aiBox.getStyleClass().add("ai-badge");

            aiRoleChips[i] = new Label("");
            aiRoleChips[i].getStyleClass().add("chip-purple");
            aiRoleChips[i].setVisible(false);

            aiBadgeLabels[i] = new Label();
            aiBadgeLabels[i].getStyleClass().add("badge-name");

            aiChipsLabels[i] = new Label();
            aiChipsLabels[i].getStyleClass().add("badge-chips");

            aiBox
                .getChildren()
                .addAll(aiRoleChips[i], aiBadgeLabels[i], aiChipsLabels[i]);

            aiBadges[i] = aiBox;
            applyRoundedClip(aiBox, 28);

            AnchorPane.setLeftAnchor(aiBox, aiX[i]);
            AnchorPane.setTopAnchor(aiBox, 55.0);
            aiBox.setVisible(false);
            canvas.getChildren().add(aiBox);

            aiFades[i] = new FadeTransition(Duration.millis(200), aiBox);
        }

        // Sofía (index 2) — shifted 30 px left, 100 px down
        AnchorPane.setLeftAnchor(aiBadges[2], aiX[2] - 65.0);
        AnchorPane.setTopAnchor(aiBadges[2], 58.0);

        // ── Pot display HBox (center) ─────────────────────────────────────
        //  [0]=hexagon ⬡  [1]=potText "BOTE: X,XXX"
        potLabel = new HBox();
        potLabel.getStyleClass().add("pot-display");

        potIconLabel = new Label("\u2B21");
        potIconLabel.getStyleClass().add("pot-icon");

        potTextLabel = new Label();
        potTextLabel.getStyleClass().add("pot-text");

        potLabel.getChildren().addAll(potIconLabel, potTextLabel);
        applyRoundedClip(potLabel, 24);

        AnchorPane.setLeftAnchor(potLabel, DISPLAY_W / 2 - 100.0);
        AnchorPane.setTopAnchor(potLabel, DISPLAY_H / 2 + 40.0);
        potLabel.setVisible(false);
        canvas.getChildren().add(potLabel);

        // ── Section labels (hidden by default, shown in future phases) ────
        commCardsSection = new Label("\u2014 CARTAS COMUNITARIAS \u2014");
        commCardsSection.getStyleClass().add("section-label");
        AnchorPane.setLeftAnchor(commCardsSection, DISPLAY_W / 2 - 70.0);
        AnchorPane.setTopAnchor(commCardsSection, 320.0);
        commCardsSection.setVisible(false);
        canvas.getChildren().add(commCardsSection);

        playerHandSection = new Label("\u2014 TU MANO \u2014");
        playerHandSection.getStyleClass().add("section-label");
        AnchorPane.setLeftAnchor(playerHandSection, DISPLAY_W / 2 - 580.0);
        AnchorPane.setTopAnchor(playerHandSection, 610.0);
        playerHandSection.setVisible(false);
        canvas.getChildren().add(playerHandSection);

        // ── Deck pile (7 stacked card backs at deck position) ──────────────
        deckPileCanvas = buildDeckPile();
        deckPileCanvas.getStyleClass().add("deck-pile");
        AnchorPane.setLeftAnchor(deckPileCanvas, 1280.0);
        AnchorPane.setTopAnchor(deckPileCanvas, 70.0);
        canvas.getChildren().add(deckPileCanvas);

        // ── Betting panel (bottom-center, hidden by default) ──────────────
        bettingPanel = new HBox();
        bettingPanel.getStyleClass().add("betting-panel");
        bettingPanel.setPickOnBounds(false); // don't let parent clip block child hit-testing
        bettingPanel.setVisible(false);
        AnchorPane.setLeftAnchor(bettingPanel, DISPLAY_W / 2 - 420.0);
        AnchorPane.setTopAnchor(bettingPanel, DISPLAY_H - 200.0);
        canvas.getChildren().add(bettingPanel);

        // ── Status bar (top-center, below AI badges) ─────────────────────
        statusBar = new HBox(10);
        statusBar.getStyleClass().add("status-bar");

        statusDot = new Label("\u25CF");  // ● filled circle
        statusDot.getStyleClass().add("status-dot");

        statusPhase = new Label("");
        statusPhase.getStyleClass().add("status-phase");

        statusTurn = new Label("");
        statusTurn.getStyleClass().add("status-turn");

        statusBar.getChildren().addAll(statusDot, statusPhase, statusTurn);
        applyRoundedClip(statusBar, 16);

        AnchorPane.setLeftAnchor(statusBar, DISPLAY_W / 2 - 240.0);
        AnchorPane.setTopAnchor(statusBar, 105.0);
        statusBar.setVisible(false);
        canvas.getChildren().add(statusBar);

        // ── Action log (bottom-right, hidden by default) ──────────────────
        actionLogContainer = new VBox();
        actionLogContainer.getStyleClass().add("action-log");
        AnchorPane.setRightAnchor(actionLogContainer, 40.0);
        AnchorPane.setBottomAnchor(actionLogContainer, 30.0);
        actionLogContainer.setVisible(true);
        actionLogContainer.setManaged(true);
        actionLogContainer.setMinWidth(160);
        actionLogContainer.setPrefWidth(160);
        actionLogContainer.setMinHeight(108);
        actionLogContainer.setPrefHeight(108);

        applyRoundedClip(actionLogContainer, 20);
        for (int i = 0; i < ACTION_LOG_MAX_LINES; i++) {
            Label line = new Label("");
            line.getStyleClass().add("action-log-line");
            line.setOpacity(0);
            actionLogContainer.getChildren().add(line);
            actionLogLabels.add(line);
            VBox.setVgrow(line, Priority.ALWAYS);
        }
        canvas.getChildren().add(actionLogContainer);

        // ── Result banner (center-bottom, hidden by default) ────────────────
        resultLabel = new Label();
        resultLabel.getStyleClass().add("result-banner");
        applyRoundedClip(resultLabel, 28);
        resultLabel.setVisible(false);

        HBox resultWrapper = new HBox(resultLabel);
        resultWrapper.setAlignment(Pos.CENTER);
        resultWrapper.setPrefWidth(DISPLAY_W);
        resultWrapper.setMouseTransparent(true); // don't steal clicks from betting panel below
        AnchorPane.setLeftAnchor(resultWrapper, 0.0);
        AnchorPane.setTopAnchor(resultWrapper, DISPLAY_H - 180.0);
        canvas.getChildren().add(resultWrapper);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Sets a callback invoked when the user confirms exit via the
     * confirmation dialog (ESC key or chrome close button).
     *
     * @param callback the action to run on confirmed exit (e.g., cleanup resources)
     */
    public void setOnExitConfirmed(Runnable callback) {
        this.onExitConfirmed = callback;
    }

    /**
     * Shows the stage. Safe to call from any thread.
     */
    public void show() {
        if (Platform.isFxApplicationThread()) {
            showInternal();
        } else {
            Platform.runLater(this::showInternal);
        }
    }

    private void showInternal() {
        if (stage != null) {
            stage.sizeToScene();
            stage.centerOnScreen();
            stage.show();
            stage.toFront();
            // Apply CSS AFTER showing — D3D pipeline is ready now
            stage.getScene().getRoot().applyCss();
            stage.getScene().getRoot().layout();
        }
    }

    /**
     * Hides the stage (same semantics as Swing {@code setVisible(false)}).
     * The stage can be re-shown with {@link #showFrame()}.
     */
    public void hideFrame() {
        if (Platform.isFxApplicationThread()) {
            hideFrameInternal();
        } else {
            Platform.runLater(this::hideFrameInternal);
        }
    }

    private void hideFrameInternal() {
        if (stage != null) {
            stage.hide();
        }
    }

    /**
     * Shows the exit confirmation dialog and, if confirmed, invokes
     * {@link #onExitConfirmed} and closes the stage.
     *
     * <p>Called from the FX thread when the user presses ESC or clicks
     * the chrome close button. Uses a nested event loop via
     * {@link ExitConfirmDialog#showAndWaitBlocking()} so the method
     * blocks synchronously on the FX thread until the user responds.
     */
    private void requestExit() {
        if (exitDialogShowing) {
            return;  // Prevent re-entrant calls
        }
        exitDialogShowing = true;
        try {
            boolean confirmed = ExitConfirmDialog.showAndWaitBlocking();
            if (confirmed) {
                if (onExitConfirmed != null) {
                    onExitConfirmed.run();
                }
                closeLatch.countDown();
                if (stage != null) {
                    // Bypass the close-request interceptor for final close
                    stage.setOnCloseRequest(e -> {});
                    stage.close();
                }
            }
        } finally {
            exitDialogShowing = false;
        }
    }

    /**
     * Shows the stage (same semantics as Swing {@code setVisible(true)}).
     * Safe to call from any thread.
     */
    public void showFrame() {
        show();
    }

    /**
     * Returns whether the stage is currently showing.
     * Safe to call from any thread.
     */
    public boolean isVisible() {
        if (stage != null) {
            return stage.isShowing();
        }
        return false;
    }

    /**
     * Blocks the calling thread until the UI has been fully built.
     * If the UI is already built, returns immediately.
     *
     * @param timeoutMs maximum time to wait in milliseconds
     * @throws IllegalStateException if the timeout expires before UI is ready
     */
    public void awaitUiReady(long timeoutMs) {
        if (uiReadyLatch == null || uiReadyLatch.getCount() == 0) {
            return;
        }
        try {
            if (!uiReadyLatch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException(
                    "GameTable UI did not become ready within " +
                        timeoutMs +
                        "ms"
                );
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                "GameTable awaitUiReady interrupted",
                ex
            );
        }
    }

    /**
     * Blocks the calling thread until the stage is closed by the user.
     */
    public void awaitClose() {
        try {
            closeLatch.await();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    // ── Game info update API ──────────────────────────────────────────────────

    /**
     * Updates the human player badge and chip display.
     *
     * @param name   player name (e.g. "Emaq")
     * @param chips  current chip count
     * @param role   role abbreviation ("D", "SB", "BB", or "")
     * @param folded whether the player has folded this hand
     */
    public void updatePlayerInfo(
        String name,
        int chips,
        String role,
        boolean folded
    ) {
        if (Platform.isFxApplicationThread()) {
            updatePlayerInfoInternal(name, chips, role, folded);
        } else {
            Platform.runLater(() ->
                updatePlayerInfoInternal(name, chips, role, folded)
            );
        }
    }

    private void updatePlayerInfoInternal(
        String name,
        int chips,
        String role,
        boolean folded
    ) {
        playerFolded = folded;

        // Update name and chips labels
        playerBadgeLabel.setText(name);
        playerChipsLabel.setText(String.format(Locale.US, "%,d", chips));

        // Update role chip with animation if role changed
        String cleanRole = (role != null) ? role : "";
        if (!cleanRole.equals(playerPrevRole)) {
            animateRoleChip(playerRoleChip, playerPrevRole, cleanRole);
            playerPrevRole = cleanRole;
        }

        // Toggle fold indicator visibility
        foldIndicator.setVisible(folded);

        // Toggle folded CSS class on the HBox container
        if (folded) {
            if (!playerBadge.getStyleClass().contains("player-badge-folded")) {
                playerBadge.getStyleClass().add("player-badge-folded");
            }
            playerBadge.getStyleClass().remove("player-badge");
        } else {
            playerBadge.getStyleClass().remove("player-badge-folded");
            if (!playerBadge.getStyleClass().contains("player-badge")) {
                playerBadge.getStyleClass().add("player-badge");
            }
        }

    }

    /**
     * Updates the AI player badges at the top of the table.
     * Supports up to 3 AI players. Extra AIs beyond 3 are silently ignored.
     *
     * @param aiPlayers list of AI players to display (max 3 visible)
     */
    public void updateAIPlayers(List<AIPlayer> aiPlayers) {
        if (Platform.isFxApplicationThread()) {
            updateAIPlayersInternal(aiPlayers);
        } else {
            Platform.runLater(() -> updateAIPlayersInternal(aiPlayers));
        }
    }

    private void updateAIPlayersInternal(List<AIPlayer> aiPlayers) {
        int count = Math.min(aiPlayers.size(), 3);

        for (int i = 0; i < 3; i++) {
            if (i < count) {
                AIPlayer ai = aiPlayers.get(i);
                String roleLabel = roleLabel(ai.getRole());

                // Update name and chips labels
                aiBadgeLabels[i].setText(ai.getName());
                aiChipsLabels[i].setText(
                    String.format(Locale.US, "%,d", ai.getChips())
                );

                // Update role chip with animation if role changed
                if (!roleLabel.equals(aiPrevRoles[i])) {
                    animateRoleChip(aiRoleChips[i], aiPrevRoles[i], roleLabel);
                    aiPrevRoles[i] = roleLabel;
                }

                aiFolded[i] = ai.isFolded();

                // Toggle CSS classes on HBox for folded state
                if (ai.isFolded()) {
                    aiBadges[i].getStyleClass().remove("ai-badge");
                    if (
                        !aiBadges[i].getStyleClass().contains("ai-badge-folded")
                    ) {
                        aiBadges[i].getStyleClass().add("ai-badge-folded");
                    }
                } else {
                    aiBadges[i].getStyleClass().remove("ai-badge-folded");
                    if (!aiBadges[i].getStyleClass().contains("ai-badge")) {
                        aiBadges[i].getStyleClass().add("ai-badge");
                    }
                }

                fadeTo(i, true);
            } else {
                fadeTo(i, false);
                aiFolded[i] = false;
            }
        }
    }

    /**
     * Updates the pot display at the center of the table.
     * Hides the label when the amount is 0.
     *
     * @param amount current pot amount
     */
    public void updatePot(int amount) {
        if (Platform.isFxApplicationThread()) {
            updatePotInternal(amount);
        } else {
            Platform.runLater(() -> updatePotInternal(amount));
        }
    }

    private void updatePotInternal(int amount) {
        if (amount <= 0) {
            potLabel.setVisible(false);
        } else {
            potTextLabel.setText(
                "BOTE: " + String.format(Locale.US, "%,d", amount)
            );
            potLabel.setVisible(true);
        }
    }

    /**
     * Sets the pot area to a free-text status message (e.g. lobby player count).
     * Unlike {@link #updatePot(int)}, this always shows the label.
     */
    public void setPotMessage(String message) {
        if (Platform.isFxApplicationThread()) {
            setPotMessageInternal(message);
        } else {
            Platform.runLater(() -> setPotMessageInternal(message));
        }
    }

    private void setPotMessageInternal(String message) {
        potTextLabel.setText(message);
        potIconLabel.setText("\u25CB"); // circle for status
        potLabel.setVisible(true);
    }

    /**
     * Resets all badges, pot, and folded states for a new hand.
     */
    /**
     * Shows betting buttons for the human player.
     * Thread-safe: delegates to FX thread when needed.
     */
    public void showBettingButtons(
        BettingRound round,
        int playerCurrentBet,
        boolean alreadyAllIn,
        Consumer<BettingRound.Action> callback
    ) {
        if (Platform.isFxApplicationThread()) {
            showBettingButtonsInternal(
                round,
                playerCurrentBet,
                alreadyAllIn,
                callback
            );
        } else {
            Platform.runLater(() ->
                showBettingButtonsInternal(
                    round,
                    playerCurrentBet,
                    alreadyAllIn,
                    callback
                )
            );
        }
    }

    private void showBettingButtonsInternal(
        BettingRound round,
        int playerCurrentBet,
        boolean alreadyAllIn,
        Consumer<BettingRound.Action> callback
    ) {
        hideBettingButtonsInternal();

        Label phaseLabel = new Label(round.getPhase().name());
        phaseLabel.getStyleClass().add("betting-phase-label");
        bettingPanel.getChildren().add(phaseLabel);

        boolean canCheck = round.canCheck(playerCurrentBet);
        boolean canBet = round.canBet();
        int callAmt = round.callAmount(playerCurrentBet);

        if (canCheck) addBettingBtn("CHECK", "btn-check", () ->
            callback.accept(BettingRound.Action.CHECK)
        );
        if (canBet) addBettingBtn("BET", "btn-bet", () ->
            callback.accept(BettingRound.Action.BET)
        );
        if (callAmt > 0) addBettingBtn("CALL " + callAmt, "btn-call", () ->
            callback.accept(BettingRound.Action.CALL)
        );
        // RAISE is valid whenever a bet has already been made (currentBet > 0)
        // or the player needs to call (callAmt > 0).  It was incorrectly hidden
        // when the player had already matched the current bet (callAmt == 0).
        if (callAmt > 0 || round.getCurrentBet() > 0) addBettingBtn("RAISE", "btn-raise", () ->
            callback.accept(BettingRound.Action.RAISE)
        );
        addBettingBtn("FOLD", "btn-fold", () ->
            callback.accept(BettingRound.Action.FOLD)
        );
        if (!alreadyAllIn) addBettingBtn("ALL IN", "btn-allin", () ->
            callback.accept(BettingRound.Action.ALL_IN)
        );

        bettingPanel.setVisible(true);
    }

    private void addBettingBtn(String text, String cssClass, Runnable action) {
        Button btn = new Button(text);
        btn.getStyleClass().addAll("betting-btn", cssClass);
        applyRoundedClip(btn, 16);
        btn.setOnAction(e -> action.run());

        bettingPanel.getChildren().add(btn);
    }

    /**
     * Hides the betting buttons panel.
     * Thread-safe: delegates to FX thread when needed.
     */
    public void hideBettingButtons() {
        if (Platform.isFxApplicationThread()) {
            hideBettingButtonsInternal();
        } else {
            Platform.runLater(this::hideBettingButtonsInternal);
        }
    }

    private void hideBettingButtonsInternal() {
        bettingPanel.getChildren().clear();
        bettingPanel.setVisible(false);
    }

    public void clearTable() {
        if (Platform.isFxApplicationThread()) {
            clearTableInternal();
        } else {
            Platform.runLater(this::clearTableInternal);
        }
    }

    private void clearTableInternal() {
        // Reset player folded state
        playerFolded = false;
        foldIndicator.setVisible(false);
        playerBadge.getStyleClass().remove("player-badge-folded");
        if (!playerBadge.getStyleClass().contains("player-badge")) {
            playerBadge.getStyleClass().add("player-badge");
        }

        // Hide all AI badges (fade out)
        for (int i = 0; i < 3; i++) {
            aiFolded[i] = false;
            fadeTo(i, false);
        }

        // Hide pot
        potLabel.setVisible(false);

        // Clear action log
        setActionLogInternal(List.of());

        // Hide result banner
        hideResultInternal();

        // Clear cards
        clearCardsInternal();
    }

    // ── Result Banner API ─────────────────────────────────────────────────────

    /**
     * Shows a result banner at the bottom of the table.
     * <p>
     * Matches Swing {@code GameView.TablePanel.drawResult}:
     * gold text for wins, red for losses.
     *
     * @param text   the result message to display
     * @param isWin   true if the human player won (gold style), false for loss (red style)
     */
    public void showResult(String text, boolean isWin) {
        if (Platform.isFxApplicationThread()) {
            showResultInternal(text, isWin);
        } else {
            Platform.runLater(() -> showResultInternal(text, isWin));
        }
    }

    private void showResultInternal(String text, boolean isWin) {
        resultLabel.setText(text);
        resultLabel.getStyleClass().removeAll("result-win", "result-loss");
        resultLabel.getStyleClass().add(isWin ? "result-win" : "result-loss");
        resultLabel.setVisible(true);
    }

    /**
     * Hides the result banner. Thread-safe.
     */
    public void hideResult() {
        if (Platform.isFxApplicationThread()) {
            hideResultInternal();
        } else {
            Platform.runLater(this::hideResultInternal);
        }
    }

    private void hideResultInternal() {
        resultLabel.setVisible(false);
        resultLabel.setText("");
        resultLabel.getStyleClass().removeAll("result-win", "result-loss");
    }

    // ── Game Over API ─────────────────────────────────────────────────────────

    /**
     * Shows a full-screen game over overlay with final chip count.
     * Matches Swing {@code GameView.TablePanel.showGameOver}.
     *
     * @param chips  final chip count (0 = bankrupt, &gt;0 = survived)
     */
    public void showGameOver(int chips) {
        if (Platform.isFxApplicationThread()) {
            showGameOverInternal(chips);
        } else {
            Platform.runLater(() -> showGameOverInternal(chips));
        }
    }

    private void showGameOverInternal(int chips) {
        // Hide all game components before showing overlay
        commCardsSection.setVisible(false);
        playerHandSection.setVisible(false);
        deckPileCanvas.setVisible(false);
        bettingPanel.setVisible(false);
        actionLogContainer.setVisible(false);
        hideResultInternal();
        clearCardsInternal();

        gameOverTitle.setText(chips > 0 ? "¡Fin del juego!" : "Game Over");
        gameOverTitle.getStyleClass().removeAll("gameover-win", "gameover-loss");
        gameOverTitle.getStyleClass().add(chips > 0 ? "gameover-win" : "gameover-loss");

        gameOverChips.setText(chips > 0
            ? "Fichas finales: " + String.format(Locale.US, "%,d", chips)
            : "¡Te quedaste sin fichas!");
        gameOverChips.getStyleClass().removeAll("gameover-win", "gameover-loss");
        gameOverChips.getStyleClass().add(chips > 0 ? "gameover-win" : "gameover-loss");
        gameOverOverlay.setVisible(true);
    }

    /**
     * Hides the game over overlay. Thread-safe.
     */
    public void hideGameOver() {
        if (Platform.isFxApplicationThread()) {
            hideGameOverInternal();
        } else {
            Platform.runLater(this::hideGameOverInternal);
        }
    }

    private void hideGameOverInternal() {
        gameOverOverlay.setVisible(false);
    }

    // ── Action Log API ──────────────────────────────────────────────────────

    /**
     * Replaces the action log with the given entries (up to 4 lines).
     * Shows the most recent entries at the bottom. Thread-safe.
     */
    public void setActionLog(List<String> log) {
        if (Platform.isFxApplicationThread()) {
            setActionLogInternal(log);
        } else {
            Platform.runLater(() -> setActionLogInternal(log));
        }
    }

    private void setActionLogInternal(List<String> log) {
        for (int i = 0; i < ACTION_LOG_MAX_LINES; i++) {
            Label line = actionLogLabels.get(i);
            int fromEnd = ACTION_LOG_MAX_LINES - 1 - i;
            int logIndex = log.size() - 1 - fromEnd;
            String previous = line.getText() == null ? "" : line.getText();
            if (logIndex >= 0 && logIndex < log.size()) {
                String text = log.get(logIndex);
                String next = text == null ? "" : text;
                if (!next.equals(previous)) {
                    line.setText(next);
                    playFadeIn(line);
                }
            } else {
                if (!previous.isBlank()) {
                    playFadeOut(line);
                } else {
                    line.setText("");
                    line.setOpacity(0);
                }
            }
        }
    }

    /**
     * Appends a single line to the action log.
     * If the log is already showing {@value #ACTION_LOG_MAX_LINES} lines,
     * the oldest line is removed. Thread-safe.
     */
    public void appendActionLog(String line) {
        if (Platform.isFxApplicationThread()) {
            appendActionLogInternal(line);
        } else {
            Platform.runLater(() -> appendActionLogInternal(line));
        }
    }

    private void appendActionLogInternal(String line) {
        if (line == null || line.isBlank()) {
            return;
        }
        List<String> current = new ArrayList<>();
        for (Label lbl : actionLogLabels) {
            String text = lbl.getText();
            if (text != null && !text.isEmpty()) {
                current.add(text);
            }
        }
        current.add(line);
        if (current.size() > ACTION_LOG_MAX_LINES) {
            current = current.subList(
                current.size() - ACTION_LOG_MAX_LINES,
                current.size()
            );
        }
        setActionLogInternal(current);
    }

    private void playFadeIn(Label label) {
        FadeTransition fade = new FadeTransition(
            ACTION_LOG_FADE_DURATION,
            label
        );
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.play();
    }

    private void playFadeOut(Label label) {
        FadeTransition fade = new FadeTransition(
            ACTION_LOG_FADE_DURATION,
            label
        );
        fade.setFromValue(label.getOpacity());
        fade.setToValue(0);
        fade.setOnFinished(evt -> label.setText(""));
        fade.play();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Clips a region so {@code -fx-background-image} respects rounded corners
     * (JavaFX does not clip background images via CSS {@code -fx-background-radius}).
     *
     * @param arcSize corner arc diameter; use 2× the CSS border/background radius
     */
    private static void applyRoundedClip(Region node, double arcSize) {
        Rectangle clip = new Rectangle();
        clip.setArcWidth(arcSize);
        clip.setArcHeight(arcSize);
        clip.widthProperty().bind(node.widthProperty());
        clip.heightProperty().bind(node.heightProperty());
        node.setClip(clip);
    }

    /**
     * Converts an {@link AIPlayer.Role} to its display abbreviation.
     * Matches the Swing GameView.TablePanel.roleLabel convention.
     */
    private static String roleLabel(AIPlayer.Role role) {
        if (role == null) return "";
        return switch (role) {
            case DEALER -> "D";
            case SMALL_BLIND -> "SB";
            case BIG_BLIND -> "BB";
            case NONE -> "";
        };
    }

    /**
     * Creates a Canvas showing the deck cover image (10 % larger than before).
     */
    private static javafx.scene.canvas.Canvas buildDeckPile() {
        double w = 90;
        double h = 130;
        javafx.scene.canvas.Canvas deck = new javafx.scene.canvas.Canvas(w, h);
        javafx.scene.canvas.GraphicsContext gc = deck.getGraphicsContext2D();

        java.io.InputStream is = GameTable.class.getResourceAsStream(
            "/sprites/deck_bg_300.png"
        );
        if (is != null) {
            javafx.scene.image.Image cover = new javafx.scene.image.Image(is);
            gc.drawImage(cover, 0, 0, w, h);
        } else {
            // Fallback: draw a single card back if image is missing
            GameCard.drawBack(gc, 0, 0);
        }

        // Single thick rounded border drawn on the canvas
        gc.setStroke(javafx.scene.paint.Color.rgb(0, 0, 0, 0.85));
        gc.setLineWidth(5);
        gc.strokeRoundRect(2, 2, w - 4, h - 4, 20, 20);

        return deck;
    }

    /**
     * Animates a role chip with a 360° spin when the role changes.
     * After the spin completes, updates the chip's CSS class and text.
     *
     * @param chip    the role chip Label to animate
     * @param oldRole previous role abbreviation
     * @param newRole new role abbreviation
     */
    private void animateRoleChip(Label chip, String oldRole, String newRole) {
        boolean hadRole = (oldRole != null && !oldRole.isEmpty());
        boolean hasRole = (newRole != null && !newRole.isEmpty());

        // If no role change and already correctly displayed, skip
        if (oldRole.equals(newRole)) return;

        // Determine if we should animate (both old and new non-empty) or just update
        if (hadRole && hasRole) {
            // Role changed → animate spin then update
            RotateTransition rt = new RotateTransition(
                Duration.millis(500),
                chip
            );
            rt.setByAngle(360);
            rt.setOnFinished(e -> {
                updateChipAppearance(chip, newRole);
            });
            rt.play();
        } else {
            // Going from/to empty — just update appearance directly
            updateChipAppearance(chip, newRole);
        }
    }

    /**
     * Updates the chip Label's CSS class and text based on the role.
     */
    private void updateChipAppearance(Label chip, String role) {
        chip.getStyleClass().removeAll(
            "chip-purple",
            "chip-white",
            "chip-none"
        );

        if (role == null || role.isEmpty()) {
            chip.setVisible(false);
            chip.setText("");
            return;
        }

        chip.setVisible(true);
        switch (role) {
            case "SB", "BB" -> {
                chip.getStyleClass().add("chip-purple");
                chip.setText(role);
            }
            case "D" -> {
                chip.getStyleClass().add("chip-white");
                chip.setText(role);
            }
            default -> {
                chip.getStyleClass().add("chip-none");
                chip.setText(role);
            }
        }
    }

    /**
     * Fades an AI badge HBox in or out with a 200ms animation.
     * Idempotent — calling with the same visibility state is a no-op.
     */
    private void fadeTo(int index, boolean visible) {
        HBox node = aiBadges[index];
        FadeTransition ft = aiFades[index];

        if (visible) {
            if (node.isVisible() && node.getOpacity() > 0.99) {
                return; // Already fully visible
            }
            ft.stop();
            node.setOpacity(0);
            node.setVisible(true);
            ft.setFromValue(0);
            ft.setToValue(1);
            ft.setOnFinished(null);
            ft.play();
        } else {
            if (!node.isVisible()) {
                return; // Already hidden
            }
            ft.stop();
            ft.setFromValue(node.getOpacity());
            ft.setToValue(0);
            ft.setOnFinished(e -> node.setVisible(false));
            ft.play();
        }
    }

    // ── Card Dealing API ──────────────────────────────────────────────────────

    /**
     * Deals the player's hand (2 cards) with stagger animation.
     * Clears previous player cards before dealing.
     */
    public void dealPlayerHand(List<Card> hand) {
        if (Platform.isFxApplicationThread()) {
            dealPlayerHandInternal(hand);
        } else {
            Platform.runLater(() -> dealPlayerHandInternal(hand));
        }
    }

    private void dealPlayerHandInternal(List<Card> hand) {
        // Don't re-animate player cards if already shown this round
        if (playerCardsDealt && !playerCards.isEmpty()) {
            return;
        }

        // Clear previous player cards
        for (GameCard gc : playerCards) {
            canvas.getChildren().remove(gc.getNode());
        }
        playerCards.clear();

        playerHandSection.setVisible(true);

        int startX = 200;
        int y = 650;
        for (int i = 0; i < hand.size(); i++) {
            GameCard gc = new GameCard();
            gc.setCard(hand.get(i));
            canvas.getChildren().add(gc.getNode());
            playerCards.add(gc);
            gc.dealTo(startX + i * 110, y, i * 180L, null);
        }

        playerCardsDealt = true;
    }

    /**
     * Deals community cards with stagger animation.
     * If total cards &lt;= 3 (flop), clears previous community cards.
     * Otherwise adds incrementally (turn, river).
     */
    public void dealCommunityCards(List<Card> cards) {
        if (Platform.isFxApplicationThread()) {
            dealCommunityCardsInternal(cards);
        } else {
            Platform.runLater(() -> dealCommunityCardsInternal(cards));
        }
    }

    private void dealCommunityCardsInternal(List<Card> cards) {
        int total = cards.size();

        // Flop: clear previous community cards
        if (total <= 3) {
            for (GameCard gc : communityCards) {
                canvas.getChildren().remove(gc.getNode());
            }
            communityCards.clear();
        }

        commCardsSection.setVisible(true);

        // Center the 5 community card slots
        int startX = (int) (DISPLAY_W / 2 - (5 * 150) / 2);
        int y = 350;

        // Only animate new cards (skip existing ones in incremental mode)
        for (int i = 0; i < total; i++) {
            if (i < communityCards.size()) continue; // skip existing

            GameCard gc = new GameCard();
            gc.setCard(cards.get(i));
            canvas.getChildren().add(gc.getNode());
            communityCards.add(gc);
            gc.dealTo(startX + i * 110, y, i * 100L, null);
        }
    }

    /**
     * Removes all player and community cards from the canvas and resets
     * section labels to hidden.
     */
    public void clearCards() {
        if (Platform.isFxApplicationThread()) {
            clearCardsInternal();
        } else {
            Platform.runLater(this::clearCardsInternal);
        }
    }

    private void clearCardsInternal() {
        for (GameCard gc : playerCards) {
            canvas.getChildren().remove(gc.getNode());
        }
        for (GameCard gc : communityCards) {
            canvas.getChildren().remove(gc.getNode());
        }
        playerCards.clear();
        communityCards.clear();
        playerCardsDealt = false;
        playerHandSection.setVisible(false);
        commCardsSection.setVisible(false);
    }

    // ── LAN Lobby API ────────────────────────────────────────────────────────

    /** Info for a remote LAN player displayed during lobby. */
    public record LanSeatInfo(String name, int chips) {}

    /**
     * Initializes the table for LAN lobby mode.
     * Shows the host/self player in the player seat and
     * placeholder text in the remote (AI) seat slots.
     *
     * @param selfName  host/self player name
     * @param selfChips host/self starting chips
     */
    public void showLanLobby(String selfName, int selfChips) {
        if (Platform.isFxApplicationThread()) {
            showLanLobbyInternal(selfName, selfChips);
        } else {
            Platform.runLater(() -> showLanLobbyInternal(selfName, selfChips));
        }
    }

    private void showLanLobbyInternal(String selfName, int selfChips) {
        // Show self in player badge
        playerFolded = false;
        foldIndicator.setVisible(false);
        playerRoleChip.setVisible(false);
        playerBadgeLabel.setText(selfName);
        playerChipsLabel.setText(String.format(Locale.US, "%,d", selfChips));
        if (!playerBadge.getStyleClass().contains("player-badge")) {
            playerBadge.getStyleClass().add("player-badge");
        }
        playerBadge.getStyleClass().remove("player-badge-folded");

        // Show "Esperando..." in all 3 AI/re mote slots
        for (int i = 0; i < 3; i++) {
            aiBadgeLabels[i].setText("Esperando…");
            aiChipsLabels[i].setText("");
            aiRoleChips[i].setVisible(false);
            aiFolded[i] = false;
            aiPrevRoles[i] = "";
            aiBadges[i].getStyleClass().remove("ai-badge-folded");
            if (!aiBadges[i].getStyleClass().contains("ai-badge")) {
                aiBadges[i].getStyleClass().add("ai-badge");
            }
            aiBadges[i].setOpacity(0.55);
            aiBadges[i].setVisible(true);
        }

        // Show pot as 0 (serves as status area during lobby)
        potTextLabel.setText("LAN  ·  Esperando jugadores…");
        potIconLabel.setText("\u25CB"); // circle instead of hexagon
        potLabel.setVisible(true);

        // Hide game-specific elements
        commCardsSection.setVisible(false);
        playerHandSection.setVisible(false);
        bettingPanel.setVisible(false);
        hideResultInternal();
        clearCardsInternal();
        gameOverOverlay.setVisible(false);
    }

    /**
     * Updates the remote player seats during LAN lobby.
     * Accepts up to 3 remote players; excess entries are ignored.
     * Empty seats continue showing "Esperando…" in dimmed style.
     *
     * @param remoteSeats list of connected remote players (0–3 entries)
     */
    public void updateLanLobbySeats(List<LanSeatInfo> remoteSeats) {
        if (Platform.isFxApplicationThread()) {
            updateLanLobbySeatsInternal(remoteSeats);
        } else {
            Platform.runLater(() -> updateLanLobbySeatsInternal(remoteSeats));
        }
    }

    private void updateLanLobbySeatsInternal(List<LanSeatInfo> remoteSeats) {
        int count = Math.min(remoteSeats.size(), 3);

        for (int i = 0; i < 3; i++) {
            if (i < count && remoteSeats.get(i) != null) {
                LanSeatInfo seat = remoteSeats.get(i);
                aiBadgeLabels[i].setText(seat.name());
                aiChipsLabels[i].setText(String.format(Locale.US, "%,d", seat.chips()));
                aiBadges[i].setOpacity(1.0);
                aiBadges[i].setVisible(true);
            } else {
                aiBadgeLabels[i].setText("Esperando…");
                aiChipsLabels[i].setText("");
                aiBadges[i].setOpacity(0.55);
                aiBadges[i].setVisible(true);
            }
        }
    }

    /**
     * Hides LAN lobby indicators and resets the table for game play.
     * Leaves the player badge visible (it gets updated by the game flow)
     * but hides the temporary lobby placeholders.
     */
    public void hideLanLobby() {
        if (Platform.isFxApplicationThread()) {
            hideLanLobbyInternal();
        } else {
            Platform.runLater(this::hideLanLobbyInternal);
        }
    }

    private void hideLanLobbyInternal() {
        // Reset pot to proper game display
        potTextLabel.setText("");
        potIconLabel.setText("\u2B21"); // restore hexagon
        potLabel.setVisible(false);

        // Only hide seats that are still "Esperando…" placeholders.
        // Seats with actual player names stay visible — they carry over into
        // the game where updateAIPlayers refreshes the display.
        for (int i = 0; i < 3; i++) {
            if ("Esperando…".equals(aiBadgeLabels[i].getText())) {
                fadeTo(i, false);
            }
        }

        // Reset player badge fold state
        playerFolded = false;
        foldIndicator.setVisible(false);
        playerBadge.getStyleClass().remove("player-badge-folded");
        if (!playerBadge.getStyleClass().contains("player-badge")) {
            playerBadge.getStyleClass().add("player-badge");
        }
    }

    // ── Status bar API ───────────────────────────────────────────────────────

    /**
     * Updates the connection status dot in the status bar.
     *
     * @param connected {@code true} for green dot, {@code false} for red dot
     */
    public void setStatusConnected(boolean connected) {
        if (Platform.isFxApplicationThread()) {
            setStatusConnectedInternal(connected);
        } else {
            Platform.runLater(() -> setStatusConnectedInternal(connected));
        }
    }

    private void setStatusConnectedInternal(boolean connected) {
        statusDot.getStyleClass().removeAll("status-dot-connected", "status-dot-disconnected");
        statusDot.getStyleClass().add(connected ? "status-dot-connected" : "status-dot-disconnected");
        statusBar.setVisible(true);
    }

    /**
     * Updates the phase label in the status bar.
     *
     * @param phase the current betting phase (PREFLOP, FLOP, TURN, RIVER)
     */
    public void setStatusPhase(String phase) {
        if (Platform.isFxApplicationThread()) {
            setStatusPhaseInternal(phase);
        } else {
            Platform.runLater(() -> setStatusPhaseInternal(phase));
        }
    }

    private void setStatusPhaseInternal(String phase) {
        statusPhase.setText(phase != null ? phase : "");
        statusBar.setVisible(true);
    }

    /**
     * Updates the turn indicator in the status bar.
     *
     * @param text  the turn message (e.g., "Tu turno", "Esperando…",
     *              "Turno de: Sofia")
     */
    public void setStatusTurn(String text) {
        if (Platform.isFxApplicationThread()) {
            setStatusTurnInternal(text);
        } else {
            Platform.runLater(() -> setStatusTurnInternal(text));
        }
    }

    private void setStatusTurnInternal(String text) {
        statusTurn.setText(text != null ? text : "");
        statusBar.setVisible(true);
    }

    /**
     * Hides the status bar. Called when the game ends.
     */
    public void hideStatusBar() {
        if (Platform.isFxApplicationThread()) {
            hideStatusBarInternal();
        } else {
            Platform.runLater(this::hideStatusBarInternal);
        }
    }

    private void hideStatusBarInternal() {
        statusBar.setVisible(false);
    }

    // ── Game-flow bridge API (migrated from Swing GameView) ──────────────────

    /**
     * Returns the UI synchronisation timeout from {@link GameSettings}.
     * Thread-safe.
     */
    public long getUiSyncTimeoutMs() {
        return GameSettings.get().getUiSyncTimeoutMs();
    }

    /**
     * Shows the player's hole cards with deal animation.
     * Delegates to {@link #dealPlayerHand(List)}.
     * Thread-safe.
     */
    public void showPlayerHand(List<Card> hand) {
        dealPlayerHand(hand);
        scheduleAnimationCompletion(hand.size(), "playerHand");
    }

    /**
     * Shows community cards and the player hand together.
     * Matches Swing {@code GameView.showCommunityCards(community, playerHand, newCardsCount)}.
     * Thread-safe.
     *
     * @param community     the community cards (all dealt so far)
     * @param playerHand    the player's hole cards
     * @param revealCount   how many community cards are NEW this phase (flop=3, turn/river=1)
     */
    public void showCommunityCards(
        List<Card> community,
        List<Card> playerHand,
        int revealCount
    ) {
        dealCommunityCards(community);
        dealPlayerHand(playerHand);
        scheduleAnimationCompletion(revealCount, "communityCards");
    }

    /**
     * Updates the human player role and AI player badges.
     * Thread-safe.
     *
     * @param humanRole  the human player's role (D, SB, BB, or NONE)
     * @param aiPlayers  list of AI players with their roles
     */
    public void showRoles(AIPlayer.Role humanRole, List<AIPlayer> aiPlayers) {
        String roleLabel = roleLabel(humanRole);
        updatePlayerInfo(latestPlayerName, latestPlayerChips, roleLabel, false);
        updateAIPlayers(aiPlayers);
    }

    /**
     * Updates the human player badge with name and chip count.
     * Preserves the current role and folded state.
     * Thread-safe.
     */
    public void showUserChips(String name, int chips) {
        latestPlayerName = name;
        latestPlayerChips = chips;
        updatePlayerInfo(name, chips, playerPrevRole, playerFolded);
    }

    /**
     * Synchronous version of {@link #showUserChips(String, int)}.
     * Blocks the calling thread until the FX thread has applied the update.
     *
     * @param name    player name
     * @param chips   current chip count
     * @param folded  whether the player is folded
     */
    public void showUserChipsSync(String name, int chips, boolean folded) {
        latestPlayerName = name;
        latestPlayerChips = chips;
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            updatePlayerInfoInternal(name, chips, playerPrevRole, folded);
            latch.countDown();
        });
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                // Fallback
                updatePlayerInfo(name, chips, playerPrevRole, folded);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            updatePlayerInfo(name, chips, playerPrevRole, folded);
        }
    }

    /**
     * Blocks until the last deal animation completes.
     * If no animation is pending, returns immediately.
     *
     * @param timeoutMs maximum wait time in milliseconds
     * @return true if animation completed, false on timeout
     */
    public boolean awaitLastAnimation(long timeoutMs) {
        CompletableFuture<Void> fut = lastAnimationFuture;
        if (fut == null) {
            return true;
        }
        try {
            fut.get(timeoutMs, TimeUnit.MILLISECONDS);
            return true;
        } catch (TimeoutException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException e) {
            return false;
        }
    }

    /**
     * Shows the betting buttons and blocks until the player clicks one
     * or the timeout expires.
     *
     * @param round           current betting round
     * @param playerCurrentBet the player's current bet in this round
     * @param timeoutMs       maximum wait time in milliseconds
     * @return the chosen action, or null on timeout
     */
    public BettingRound.Action awaitPlayerAction(
        BettingRound round,
        int playerCurrentBet,
        long timeoutMs
    ) {
        CompletableFuture<BettingRound.Action> future = new CompletableFuture<>();
        pendingActionFuture = future;

        showBettingButtons(round, playerCurrentBet, false, action -> {
            if (future.complete(action)) {
                pendingActionFuture = null;
            }
        });

        try {
            BettingRound.Action result = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            hideBettingButtons();
            return result;
        } catch (TimeoutException e) {
            hideBettingButtons();
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            hideBettingButtons();
            return null;
        } catch (ExecutionException e) {
            hideBettingButtons();
            return null;
        }
    }

    /**
     * Shows the fold indicator on the player badge.
     * Thread-safe.
     */
    public void showPlayerFolded() {
        if (Platform.isFxApplicationThread()) {
            showPlayerFoldedInternal();
        } else {
            Platform.runLater(this::showPlayerFoldedInternal);
        }
    }

    private void showPlayerFoldedInternal() {
        playerFolded = true;
        foldIndicator.setVisible(true);
        if (!playerBadge.getStyleClass().contains("player-badge-folded")) {
            playerBadge.getStyleClass().add("player-badge-folded");
        }
        playerBadge.getStyleClass().remove("player-badge");
    }

    /**
     * Highlights the badge of the active player with a golden glow.
     * Removes the glow from all other badges.
     *
     * @param playerId the playerId of the active player, or null to clear all highlights
     * @param playerIdToName map of playerId → display name to find the correct badge
     */
    public void highlightActivePlayer(String playerId, java.util.Map<String, String> playerIdToName) {
        if (Platform.isFxApplicationThread()) {
            highlightActivePlayerInternal(playerId, playerIdToName);
        } else {
            Platform.runLater(() -> highlightActivePlayerInternal(playerId, playerIdToName));
        }
    }

    private void highlightActivePlayerInternal(String playerId, java.util.Map<String, String> playerIdToName) {
        // Remove glow from all badges
        playerBadge.getStyleClass().remove("badge-active-turn");
        for (int i = 0; i < 3; i++) {
            aiBadges[i].getStyleClass().remove("badge-active-turn");
        }

        if (playerId == null || playerIdToName == null) {
            return;
        }

        // Find the matching name for this playerId
        String targetName = playerIdToName.get(playerId);
        if (targetName == null) {
            return;
        }

        // Check if this is the local player badge
        if (targetName.equals(playerBadgeLabel.getText())) {
            playerBadge.getStyleClass().add("badge-active-turn");
            return;
        }

        // Check AI/remote badge slots
        for (int i = 0; i < 3; i++) {
            if (targetName.equals(aiBadgeLabels[i].getText())) {
                aiBadges[i].getStyleClass().add("badge-active-turn");
                return;
            }
        }
    }

    /**
     * Resolves a pending player action manually (used on timeout fallback).
     * Shows a brief result banner about the auto-action.
     *
     * @param action the action to resolve with
     */
    public void resolvePendingPlayerAction(BettingRound.Action action) {
        CompletableFuture<BettingRound.Action> future = pendingActionFuture;
        if (future != null && future.complete(action)) {
            pendingActionFuture = null;
        }
        if (action == BettingRound.Action.FOLD) {
            showUserChips(latestPlayerName, latestPlayerChips);
            Platform.runLater(() -> {
                foldIndicator.setVisible(true);
                playerFolded = true;
            });
        }
    }

    /**
     * Opens the bet amount dialog and blocks until the player chooses an amount.
     *
     * @param minBet minimum bet (e.g. big blind or min raise)
     * @param maxBet maximum bet (player's total chips)
     * @return the chosen bet amount, or minBet on cancel/timeout
     */
    public int getPlayerBetAmount(int minBet, int maxBet) {
        CompletableFuture<Integer> future = BetAmountDialog.showAndWait(minBet, maxBet);
        try {
            return future.get(60, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return minBet;
        } catch (Exception e) {
            return minBet;
        }
    }

    /**
     * Shows the "Play again?" dialog and blocks until the player answers.
     *
     * @param chips current chip count for display
     * @return true if the player wants to continue, false otherwise
     */
    public boolean askPlayAgain(int chips) {
        CompletableFuture<Boolean> future = ContinueDialog.showAndWait(chips, stage);
        try {
            Boolean result = future.get(60, TimeUnit.SECONDS);
            return result != null && result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Closes the GameTable stage and signals any threads blocked on {@link #awaitClose()}.
     * Thread-safe.  Does NOT show an exit confirmation dialog — this is a programmatic
     * shutdown (e.g. after "play again" → no, or from cleanup code).
     */
    public void requestGracefulShutdown() {
        if (Platform.isFxApplicationThread()) {
            shutdownInternal();
        } else {
            Platform.runLater(this::shutdownInternal);
        }
    }

    private void shutdownInternal() {
        // Release any threads blocked on the close latch
        if (closeLatch != null) {
            closeLatch.countDown();
        }
        // Release the UI-ready latch so any awaiting threads unblock
        if (uiReadyLatch != null) {
            uiReadyLatch.countDown();
        }
        // Cancel any pending player action so threads blocked on awaitPlayerAction unblock
        CompletableFuture<BettingRound.Action> fut = pendingActionFuture;
        if (fut != null && !fut.isDone()) {
            fut.cancel(true);
            pendingActionFuture = null;
        }
        if (stage != null) {
            // Bypass the close-request interceptor (no exit dialog)
            stage.setOnCloseRequest(e -> {});
            stage.close();
        }
    }

    /**
     * No-op for GameTable: the table is always "active" once shown.
     * Provided for compatibility with the Swing GameView API.
     */
    public void setGameActive(boolean active) {
        // GameTable is always active once shown — no toggle needed
    }

    /**
     * Schedules a CompletableFuture completion based on expected deal animation duration.
     * The total animation time is computed from card count and stagger delays.
     */
    private void scheduleAnimationCompletion(int cardCount, String label) {
        // Stagger delay: 180ms per player card, 100ms per community card
        long staggerMs = label.startsWith("playerHand") ? 180L : 100L;
        long totalMs = (cardCount - 1) * staggerMs + 600L; // 400ms fly + 200ms margin

        CompletableFuture<Void> future = new CompletableFuture<>();
        lastAnimationFuture = future;

        Thread timer = new Thread(() -> {
            try {
                Thread.sleep(totalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            future.complete(null);
            lastAnimationFuture = null;
        }, "fx-anim-timer");
        timer.setDaemon(true);
        timer.start();
    }

    // ── Internal API ──────────────────────────────────────────────────────────

    /**
     * Disposes the stage and releases resources.
     * Called when a new instance replaces the old singleton.
     */
    private void dispose() {
        if (stage != null) {
            stage.close();
            stage = null;
        }
        // Release any threads blocked on the old latch
        if (uiReadyLatch != null) {
            uiReadyLatch.countDown();
            uiReadyLatch = null;
        }
    }

    // ── Test support ──────────────────────────────────────────────────────────

    /**
     * Exposes the internal Stage for test inspection.
     * Not part of the public API contract.
     */
    Stage getStageForTest() {
        return stage;
    }
}
