package view;

import model.AIPlayer;
import model.BettingRound;
import model.Card;
import model.PokerGame;
import model.Player;
import model.ShowdownResult;

import javax.sound.sampled.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * GameView — Casino-style Swing UI
 * Fully custom-painted. No system L&F dependency → identical on Linux & Windows.
 */
public class GameView {

    // ── Palette ──────────────────────────────────────────────────────────────
    private static final Color FELT_DARK   = new Color(0x0B3220);
    private static final Color FELT_MID    = new Color(0x145233);
    private static final Color FELT_LIGHT  = new Color(0x1A6B42);
    private static final Color GOLD        = new Color(0xC9A84C);
    private static final Color GOLD_LIGHT  = new Color(0xE8C96A);
    private static final Color GOLD_DIM    = new Color(0x8B6914);
    private static final Color CREAM       = new Color(0xF5EDD8);
    private static final Color RED_SUIT    = new Color(0xC0392B);
    private static final Color DARK_BG     = new Color(0x060E0A);
    private static final Color PANEL_BG    = new Color(0x0D2B1A);

    // ── Fonts ─────────────────────────────────────────────────────────────────
    private static final Font FONT_TITLE  = new Font("Serif",  Font.BOLD,  28);
    private static final Font FONT_LABEL  = new Font("Serif",  Font.BOLD,  15);
    private static final Font FONT_MONO   = new Font("Monospaced", Font.PLAIN, 13);
    private static final Font FONT_CARD_R = new Font("Serif",  Font.BOLD,  22);
    private static final Font FONT_CARD_S = new Font("Serif",  Font.PLAIN, 18);
    private static final Font FONT_BTN    = new Font("Serif",  Font.BOLD,  14);
    private static final Font FONT_CHIP   = new Font("Monospaced", Font.BOLD, 20);

    // ── Main window ───────────────────────────────────────────────────────────
    private JFrame frame;
    private TablePanel tablePanel;

    // ── Animation timer ───────────────────────────────────────────────────────
    private javax.swing.Timer repaintTimer;
    private final AtomicBoolean shutdownRequested = new AtomicBoolean(false);

// ═══════════════════════════════════════════════════════════════════════
    //  UI THREAD SYNCHRONIZATION CONTRACTS
    // ═══════════════════════════════════════════════════════════════════════
    // Pattern: Game thread waits on signals from EDT, never blocks EDT.
    //
    // 1. UI Ready Barrier: countDown() called at end of buildFrame() in EDT.
    //    Game thread calls awaitUiReady() before any UI interaction.
    //
    // 2. EDT Bridge: runOnEdtAndWait/callOnEdtAndWait use invokeAndWait with
    //    isEventDispatchThread() guard to prevent self-deadlock.
    //
    // 3. Input/Animation Futures: CompletableFuture completed from EDT callbacks.
    //    Game thread awaits with timeout, receives null on timeout → fallback.
    //
    // Timeout fallback: CHECK if valid, otherwise FOLD.
    // ═══════════════════════════════════════════════════════════════════════
    private final CountDownLatch uiReadyLatch = new CountDownLatch(1);
    private volatile CompletableFuture<BettingRound.Action> pendingActionFuture;
    private volatile CompletableFuture<Boolean> animationFuture;

    private static final long UI_SYNC_TIMEOUT_MS = 45000L; // 45 seconds - standard poker tournament time

    // =========================================================================
    //  CONSTRUCTOR
    // =========================================================================
    public GameView() {
        this(true);
    }

    protected GameView(boolean initializeUi) {
        if (!initializeUi) {
            return;
        }
        SwingUtilities.invokeLater(this::buildFrame);
    }

    private void buildFrame() {
        // Force cross-platform L&F — looks identical everywhere
        try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); }
        catch (Exception ignored) {}

        frame = new JFrame("♠  Royal Poker  ♠");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(900, 640));
        frame.setPreferredSize(new Dimension(1050, 700));

        tablePanel = new TablePanel();
        frame.setContentPane(tablePanel);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        // Global repaint loop for animations
        repaintTimer = new javax.swing.Timer(16, e -> tablePanel.repaint());
        repaintTimer.start();

        signalUiReady();
    }

    void signalUiReady() {
        uiReadyLatch.countDown();
    }

    public boolean awaitUiReady(long timeoutMs) {
        return awaitLatch(uiReadyLatch, timeoutMs, "ui-ready");
    }

    public void runOnEdtAndWait(Runnable task) {
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
            return;
        }

        try {
            SwingUtilities.invokeAndWait(task);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to run task on EDT", ex);
        }
    }

    public <T> T callOnEdtAndWait(Callable<T> task) {
        if (SwingUtilities.isEventDispatchThread()) {
            try {
                return task.call();
            } catch (Exception ex) {
                throw new RuntimeException("Failed to run task on EDT", ex);
            }
        }

        FutureTask<T> futureTask = new FutureTask<>(task);
        try {
            SwingUtilities.invokeAndWait(futureTask);
            return futureTask.get();
        } catch (Exception ex) {
            throw new RuntimeException("Failed to run task on EDT", ex);
        }
    }

    // =========================================================================
    //  PUBLIC API  (mirrors original GameView)
    // =========================================================================

    /** Replaces JOptionPane.showInputDialog for player name */
    public String getUserName() {
        // Show name-entry dialog on EDT, block until done
        try {
            String result = callOnEdtAndWait(this::showNameDialog);
            return result == null ? "Jugador" : result;
        } catch (RuntimeException ex) {
            return "Jugador";
        }
    }

    public void showJoinRejectionMessage(String message) {
        Runnable showDialog = () -> JOptionPane.showMessageDialog(
            frame,
            message,
            "No se pudo unir",
            JOptionPane.WARNING_MESSAGE
        );
        if (SwingUtilities.isEventDispatchThread()) {
            showDialog.run();
            return;
        }

        try {
            SwingUtilities.invokeAndWait(showDialog);
        } catch (Exception ignored) {
        }
    }

    public boolean askRetryJoin() {
        return callOnEdtAndWait(() -> {
            int option = JOptionPane.showConfirmDialog(
                frame,
                "¿Quieres intentar con otro nombre?",
                "Reintentar ingreso",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
            );
            return option == JOptionPane.YES_OPTION;
        });
    }

    /** Replaces showUserChips */
    public void showUserChips(String userName, int chips) {
        SwingUtilities.invokeLater(() -> tablePanel.setPlayerInfo(userName, chips));
    }

    /** Replaces showPlayerHand — animates cards from deck pile */
    public void showPlayerHand(ArrayList<Card> hand) {
        CompletableFuture<Boolean> completion = new CompletableFuture<>();
        animationFuture = completion;

        SwingUtilities.invokeLater(() -> {
            tablePanel.dealPlayerHand(hand);
            SoundFX.playDeal();
        });
        // last card starts at (size-1)*180ms, takes 400ms to fly + 200ms margin
        long wait = (hand.size() - 1) * 180L + 600L;
        scheduleAnimationCompletion(completion, wait);
    }

    /** Muestra cartas comunitarias sin resultado (para fases intermedias) */
    public void showCommunityCards(ArrayList<Card> community, ArrayList<Card> playerHand) {
        CompletableFuture<Boolean> completion = new CompletableFuture<>();
        animationFuture = completion;

        SwingUtilities.invokeLater(() -> {
            tablePanel.dealCommunity(community);
            SoundFX.playDeal();
        });
        long wait = (community.size() - 1) * 150L + 600L;
        scheduleAnimationCompletion(completion, wait);
    }

    /** Muestra roles de cada jugador en la mesa */
    public void showRoles(AIPlayer.Role humanRole, List<AIPlayer> aiPlayers) {
        SwingUtilities.invokeLater(() -> tablePanel.setRoles(humanRole, aiPlayers));
    }

    /** Actualiza el bote visible */
    public void showPot(int pot) {
        SwingUtilities.invokeLater(() -> tablePanel.setPot(pot));
    }

    /** Muestra el log de acciones de la IA */
    public void showAIActions(List<String> log) {
        SwingUtilities.invokeLater(() -> tablePanel.setActionLog(log));
        scheduleAnimationCompletion(new CompletableFuture<>(), 600); // pausa para que el jugador pueda leerlo
    }

    /** Indica que el jugador se retiró */
    public void showPlayerFolded() {
        SwingUtilities.invokeLater(() -> tablePanel.setPlayerFolded());
    }

    /**
     * Bloquea el hilo del juego hasta que el jugador elige una acción.
     * Muestra los botones correspondientes según el estado de la ronda.
     */
    public BettingRound.Action waitForPlayerAction(BettingRound round, int playerCurrentBet) {
        return waitForPlayerAction(round, playerCurrentBet, UI_SYNC_TIMEOUT_MS);
    }

    public BettingRound.Action waitForPlayerAction(BettingRound round, int playerCurrentBet, long timeoutMs) {
        CompletableFuture<BettingRound.Action> future = new CompletableFuture<>();
        pendingActionFuture = future;

        SwingUtilities.invokeLater(() ->
            tablePanel.showBettingButtons(round, playerCurrentBet, false, action -> {
                if (future.complete(action)) {
                    pendingActionFuture = null;
                }
            })
        );

        BettingRound.Action action = awaitFuture(future, timeoutMs, "player-action");
        SwingUtilities.invokeLater(() -> tablePanel.hideBettingButtons());
        return action;
    }

    public void resolvePendingPlayerAction(BettingRound.Action action) {
        CompletableFuture<BettingRound.Action> future = pendingActionFuture;
        if (future == null) {
            return;
        }
        if (future.complete(action)) {
            pendingActionFuture = null;
        }
    }

    public long getUiSyncTimeoutMs() {
        return UI_SYNC_TIMEOUT_MS;
    }

    /**
     * Pide al jugador que ingrese un monto de apuesta.
     * Usa invokeAndWait para mostrar el diálogo en el EDT sin deadlock.
     */
    public int getPlayerBetAmount(int minBet, int maxBet) {
        try {
            return callOnEdtAndWait(() -> {
                JPanel panel = new JPanel(new BorderLayout(8, 8));
                panel.setBackground(new Color(0x0D2B1A));

                JLabel lbl = new JLabel("Monto (" + minBet + " – " + maxBet + "):");
                lbl.setForeground(CREAM);
                lbl.setFont(FONT_LABEL);

                int safeMax = Math.max(minBet, maxBet);
                JSlider slider = new JSlider(minBet, safeMax, minBet);
                slider.setBackground(new Color(0x0D2B1A));
                slider.setForeground(GOLD);
                slider.setMajorTickSpacing((safeMax - minBet) / 4 + 1);
                slider.setPaintTicks(true);

                JLabel valLabel = new JLabel(String.valueOf(minBet));
                valLabel.setForeground(GOLD_LIGHT);
                valLabel.setFont(FONT_CHIP);
                slider.addChangeListener(e -> valLabel.setText(String.valueOf(slider.getValue())));

                panel.add(lbl,      BorderLayout.NORTH);
                panel.add(slider,   BorderLayout.CENTER);
                panel.add(valLabel, BorderLayout.SOUTH);

                int opt = JOptionPane.showConfirmDialog(frame, panel, "¿Cuánto apostás?",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

                return (opt == JOptionPane.OK_OPTION) ? slider.getValue() : minBet;
            });
        } catch (RuntimeException ex) {
            return minBet;
        }
    }

    /** Muestra el resultado final con el bote y el ganador */
    public void showResult(ArrayList<Card> community, ArrayList<Card> playerHand,
                           ShowdownResult showdownResult, int pot) {
        if (showdownResult == null || showdownResult.getWinners().isEmpty()) {
            if (tablePanel != null) {
                SwingUtilities.invokeLater(() -> tablePanel.showResult(showdownResult, pot));
            }
            scheduleAnimationCompletion(new CompletableFuture<>(), 2500);
            return;
        }

        if (!showdownResult.isTie()) {
            Player winner = showdownResult.getWinners().get(0);
            boolean humanWon = winner instanceof model.User;
            String aiWinnerName = humanWon ? null : winner.getName();
            showResult(community, playerHand, showdownResult.getBestRank(), pot, humanWon, aiWinnerName);
            return;
        }

        if (tablePanel != null) {
            SwingUtilities.invokeLater(() -> tablePanel.showResult(showdownResult, pot));
        }
        scheduleAnimationCompletion(new CompletableFuture<>(), 2500);
    }

    /**
     * Compatibilidad con firmas previas.
     */
    public void showResult(ArrayList<Card> community, ArrayList<Card> playerHand,
                           PokerGame.HandRank bestHand, int pot,
                           boolean humanWon, String aiWinnerName) {
        SwingUtilities.invokeLater(() -> tablePanel.showResult(bestHand, pot, humanWon, aiWinnerName));
        scheduleAnimationCompletion(new CompletableFuture<>(), 2500);
    }

    /** Pregunta si el jugador quiere jugar otra mano. Bloquea hasta respuesta. */
    public boolean askPlayAgain(int chips) {
        return callOnEdtAndWait(() -> {
            int opt = JOptionPane.showConfirmDialog(frame,
                "Fichas: " + String.format("%,d", chips) + "\n¿Jugar otra mano?",
                "¿Seguir jugando?",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);
            return opt == JOptionPane.YES_OPTION;
        });
    }

    /** Pantalla de fin de juego */
    public void showGameOver(int chips) {
        SwingUtilities.invokeLater(() -> tablePanel.showGameOver(chips));
    }

    public void requestGracefulShutdown() {
        if (!shutdownRequested.compareAndSet(false, true)) {
            return;
        }

        Runnable shutdownTask = () -> {
            if (repaintTimer != null && repaintTimer.isRunning()) {
                repaintTimer.stop();
            }

            if (frame != null) {
                frame.setVisible(false);
                frame.dispose();
            }

            onGracefulShutdownComplete();
        };

        if (SwingUtilities.isEventDispatchThread()) {
            shutdownTask.run();
            return;
        }

        try {
            SwingUtilities.invokeAndWait(shutdownTask);
        } catch (Exception ignored) {
            onGracefulShutdownComplete();
        }
    }

    protected void onGracefulShutdownComplete() {
        terminateProcess(0);
    }

    protected void terminateProcess(int statusCode) {
        System.exit(statusCode);
    }

    /** Limpia estado visual transitorio antes de una nueva mano */
    public void clearTableForNewHand() {
        if (tablePanel == null) {
            return;
        }
        try {
            SwingUtilities.invokeAndWait(() -> tablePanel.clearRoundVisualState());
        } catch (Exception ignored) {
            SwingUtilities.invokeLater(() -> tablePanel.clearRoundVisualState());
        }
    }

    // =========================================================================
    //  NAME DIALOG
    // =========================================================================
    private String showNameDialog() {
        CasinoDialog dialog = new CasinoDialog(frame, "Bienvenido al Royal Poker", "Ingresa tu nombre:");
        dialog.setVisible(true);
        return dialog.getResult();
    }

    // =========================================================================
    //  HELPERS
    // =========================================================================
    private void scheduleAnimationCompletion(CompletableFuture<Boolean> completion, long waitMs) {
        animationFuture = completion;
        Timer timer = new Timer("ui-animation", true);
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                completion.complete(true);
                animationFuture = null;
                timer.cancel();
            }
        }, waitMs);
    }

    public boolean awaitLastAnimation(long timeoutMs) {
        CompletableFuture<Boolean> current = animationFuture;
        if (current == null) {
            return true;
        }
        return awaitFuture(current, timeoutMs, "animation");
    }

    private static <T> T awaitFuture(CompletableFuture<T> future, long timeoutMs, String label) {
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            System.out.println("Timeout esperando " + label + " (" + timeoutMs + "ms)");
            return null;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            System.out.println("Interrumpido esperando " + label);
            return null;
        } catch (ExecutionException ex) {
            System.out.println("Error esperando " + label + ": " + ex.getCause());
            return null;
        }
    }

    private static boolean awaitLatch(CountDownLatch latch, long timeoutMs, String label) {
        try {
            boolean completed = latch.await(timeoutMs, TimeUnit.MILLISECONDS);
            if (!completed) {
                System.out.println("Timeout esperando " + label + " (" + timeoutMs + "ms)");
            }
            return completed;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            System.out.println("Interrumpido esperando " + label);
            return false;
        }
    }

    // =========================================================================
    //  TABLE PANEL  — the main playing surface
    // =========================================================================
    class TablePanel extends JPanel {

        // Displayed data
        private String playerName   = "";
        private int    chips        = 0;
        private int    displayChips = 0;
        private List<CardSprite> playerSprites    = new ArrayList<>();
        private List<CardSprite> communitySprites = new ArrayList<>();
        private String resultMessage = null;
        private Color  resultColor   = GOLD;

        // Roles & pot
        private AIPlayer.Role humanRole = AIPlayer.Role.NONE;
        private List<AIPlayer> aiPlayers = new ArrayList<>();
        private int pot = 0;
        private boolean playerFolded = false;

        // Action log (IA decisions)
        private List<String> actionLog = new ArrayList<>();

        // Betting buttons panel
        private JPanel bettingPanel = null;

        // Deck pile position
        private static final int DECK_X = 820;
        private static final int DECK_Y = 80;

        // Chip animation
        private long chipAnimStart = -1;
        private int  chipAnimFrom  = 0;
        private int  chipAnimTo    = 0;

        // Functional interface for callbacks
        interface ActionCallback  { void onAction(BettingRound.Action action); }
        interface AmountCallback  { void onAmount(int amount); }

        TablePanel() {
            setBackground(DARK_BG);
            setLayout(null);
        }

        // ── Setters ──────────────────────────────────────────────────────────

        void setPlayerInfo(String name, int ch) {
            this.playerName    = name;
            this.chipAnimFrom  = displayChips;
            this.chipAnimTo    = ch;
            this.chipAnimStart = System.currentTimeMillis();
            SoundFX.playChips();
        }

        void setRoles(AIPlayer.Role humanRole, List<AIPlayer> ais) {
            this.humanRole  = humanRole;
            this.aiPlayers  = new ArrayList<>(ais);
        }

        void setPot(int pot)               { this.pot = pot; }
        void setPlayerFolded()             { this.playerFolded = true; }
        void setActionLog(List<String> log){ this.actionLog = new ArrayList<>(log); }

        void clearRoundVisualState() {
            playerSprites.clear();
            communitySprites.clear();
            actionLog.clear();
            playerFolded = false;
            resultMessage = null;
            pot = 0;
            hideBettingButtons();
            revalidate();
            repaint();
        }

        void dealPlayerHand(List<Card> hand) {
            playerSprites.clear();
            resultMessage = null;
            playerFolded  = false;
            int startX = 120, y = 420;
            for (int i = 0; i < hand.size(); i++) {
                playerSprites.add(new CardSprite(hand.get(i), startX + i * 100, y, DECK_X, DECK_Y, i * 180L));
            }
        }

        void dealCommunity(List<Card> comm) {
            communitySprites.clear();
            int total  = comm.size();
            int startX = getWidth() / 2 - (total * 90) / 2;
            int y = 220;
            for (int i = 0; i < total; i++) {
                communitySprites.add(new CardSprite(comm.get(i), startX + i * 90, y, DECK_X, DECK_Y, i * 150L));
            }
        }

        void showResult(ShowdownResult showdownResult, int finalPot) {
            if (showdownResult == null || showdownResult.getWinners().isEmpty()) {
                resultMessage = "Sin ganador definido";
                resultColor = new Color(0xCC4444);
                return;
            }

            PokerGame.HandRank bestHand = showdownResult.getBestRank();
            if (showdownResult.isTie()) {
                String winnerList = joinWinnerNames(showdownResult.getWinners());
                resultMessage = "Empate: " + winnerList + " dividen " + String.format("%,d", finalPot)
                    + "  |  " + bestHand.spanishName;
                resultColor = GOLD_LIGHT;
                return;
            }

            Player winner = showdownResult.getWinners().get(0);
            if (winner instanceof model.User) {
                resultMessage = "¡Ganaste! +" + String.format("%,d", finalPot)
                              + "  |  " + bestHand.spanishName;
                resultColor   = GOLD_LIGHT;
                if (bestHand.ordinal() >= PokerGame.HandRank.THREE_OF_A_KIND.ordinal()) SoundFX.playWin();
            } else {
                resultMessage = winner.getName() + " gana el bote de " + String.format("%,d", finalPot)
                              + "  |  Tu mano: " + bestHand.spanishName;
                resultColor   = new Color(0xCC4444);
            }
        }

        // Sobrecarga para compatibilidad interna
        void showResult(PokerGame.HandRank bestHand, int finalPot, boolean humanWon, String aiWinnerName) {
            if (humanWon) {
                resultMessage = "¡Ganaste! +" + String.format("%,d", finalPot)
                    + "  |  " + bestHand.spanishName;
                resultColor = GOLD_LIGHT;
                if (bestHand.ordinal() >= PokerGame.HandRank.THREE_OF_A_KIND.ordinal()) SoundFX.playWin();
            } else {
                resultMessage = aiWinnerName + " gana el bote de " + String.format("%,d", finalPot)
                    + "  |  Tu mano: " + bestHand.spanishName;
                resultColor = new Color(0xCC4444);
            }
        }

        void showResult(PokerGame.HandRank bestHand, int finalPot) { showResult(bestHand, finalPot, false, ""); }
        void showResult(PokerGame.HandRank bestHand) { showResult(bestHand, pot, false, ""); }

        private String joinWinnerNames(List<Player> winners) {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < winners.size(); i++) {
                if (i > 0) {
                    builder.append(", ");
                }
                builder.append(winners.get(i).getName());
            }
            return builder.toString();
        }

        void showGameOver(int finalChips) {
            resultMessage = finalChips > 0
                ? "¡Fin del juego!  Fichas finales: " + String.format("%,d", finalChips)
                : "¡Te quedaste sin fichas!  Game Over";
            resultColor = finalChips > 0 ? GOLD_LIGHT : new Color(0xCC4444);
        }

        // ── Betting buttons ───────────────────────────────────────────────────

        void showBettingButtons(BettingRound round, int playerCurrentBet, boolean alreadyAllIn, ActionCallback callback) {
            hideBettingButtons();

            bettingPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 6)) {
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g;
                    g2.setColor(new Color(0x0A1E12, true));
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
                    g2.setColor(GOLD_DIM);
                    g2.setStroke(new BasicStroke(1.5f));
                    g2.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, 14, 14);
                }
            };
            bettingPanel.setOpaque(false);

            JLabel phaseLabel = new JLabel(round.getPhase().name());
            phaseLabel.setFont(new Font("Serif", Font.BOLD, 13));
            phaseLabel.setForeground(GOLD);
            bettingPanel.add(phaseLabel);

            boolean canCheck = round.canCheck(playerCurrentBet);
            boolean canBet   = round.canBet();
            int callAmt      = round.callAmount(playerCurrentBet);

            if (canCheck)    addBtn(bettingPanel, "CHECK",          FELT_MID,              () -> callback.onAction(BettingRound.Action.CHECK));
            if (canBet)      addBtn(bettingPanel, "BET",            GOLD_DIM,              () -> callback.onAction(BettingRound.Action.BET));
            if (callAmt > 0) addBtn(bettingPanel, "CALL " + callAmt, new Color(0x1A5C8A), () -> callback.onAction(BettingRound.Action.CALL));
            if (callAmt > 0) addBtn(bettingPanel, "RAISE",          new Color(0x7A3A00),  () -> callback.onAction(BettingRound.Action.RAISE));
                             addBtn(bettingPanel, "FOLD",            new Color(0x6B1414),  () -> callback.onAction(BettingRound.Action.FOLD));
            if (!alreadyAllIn) addBtn(bettingPanel, "ALL IN",       new Color(0x8B0000),  () -> callback.onAction(BettingRound.Action.ALL_IN));

            int W = getWidth(), H = getHeight();
            bettingPanel.setBounds(W/2 - 320, H - 100, 640, 60);
            add(bettingPanel);
            revalidate();
            repaint();
        }

        void hideBettingButtons() {
            if (bettingPanel != null) {
                remove(bettingPanel);
                bettingPanel = null;
                revalidate();
                repaint();
            }
        }

        private void addBtn(JPanel parent, String label, Color bg, Runnable action) {
            JButton btn = new JButton(label) {
                private boolean hov = false;
                { setOpaque(false); setContentAreaFilled(false); setBorderPainted(false);
                  setFocusPainted(false); setFont(FONT_BTN); setForeground(CREAM);
                  setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                  addMouseListener(new MouseAdapter() {
                      public void mouseEntered(MouseEvent e) { hov = true;  repaint(); }
                      public void mouseExited (MouseEvent e) { hov = false; repaint(); }
                  });
                }
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g;
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(hov ? bg.brighter() : bg);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                    super.paintComponent(g);
                }
                @Override public Dimension getPreferredSize() { return new Dimension(90, 36); }
            };
            btn.addActionListener(e -> action.run());
            parent.add(btn);
        }

        // ── Paint ─────────────────────────────────────────────────────────────

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);

            int W = getWidth(), H = getHeight();

            drawBackground(g2, W, H);
            drawFeltTable(g2, W, H);
            drawDeckPile(g2);
            drawLabels(g2, W, H);
            drawChipCounter(g2, W, H);
            drawPot(g2, W, H);
            drawRoleBadges(g2, W, H);
            drawActionLog(g2, W, H);
            drawCards(g2);
            drawResult(g2, W, H);
            animateChips();
        }

        private void drawBackground(Graphics2D g2, int W, int H) {
            GradientPaint bg = new GradientPaint(0, 0, new Color(0x060E0A), W, H, new Color(0x0A1F12));
            g2.setPaint(bg);
            g2.fillRect(0, 0, W, H);

            // Subtle diagonal texture lines
            g2.setColor(new Color(255, 255, 255, 8));
            g2.setStroke(new BasicStroke(0.5f));
            for (int x = -H; x < W + H; x += 38) {
                g2.drawLine(x, 0, x + H, H);
            }
        }

        private void drawFeltTable(Graphics2D g2, int W, int H) {
            // Oval felt surface
            int tx = 30, ty = 40, tw = W - 60, th = H - 120;
            Ellipse2D oval = new Ellipse2D.Double(tx, ty, tw, th);

            // Felt gradient
            RadialGradientPaint felt = new RadialGradientPaint(
                new Point2D.Double(W / 2.0, H / 2.0),
                Math.max(tw, th) / 2.0f,
                new float[]{0f, 0.6f, 1f},
                new Color[]{FELT_LIGHT, FELT_MID, FELT_DARK}
            );
            g2.setPaint(felt);
            g2.fill(oval);

            // Gold border — outer glow
            g2.setStroke(new BasicStroke(6f));
            g2.setColor(GOLD_DIM);
            g2.draw(oval);
            g2.setStroke(new BasicStroke(2.5f));
            g2.setColor(GOLD);
            g2.draw(oval);

            // Inner ring
            Ellipse2D inner = new Ellipse2D.Double(tx + 14, ty + 14, tw - 28, th - 28);
            g2.setStroke(new BasicStroke(1f));
            g2.setColor(new Color(201, 168, 76, 80));
            g2.draw(inner);
        }

        private void drawDeckPile(Graphics2D g2) {
            // Draw stacked card backs (deck pile)
            for (int i = 6; i >= 0; i--) {
                drawCardBack(g2, DECK_X + i, DECK_Y - i, 62, 88);
            }
            // "DECK" label
            g2.setFont(new Font("Serif", Font.BOLD, 11));
            g2.setColor(GOLD);
            FontMetrics fm = g2.getFontMetrics();
            String lbl = "MAZO";
            g2.drawString(lbl, DECK_X + 31 - fm.stringWidth(lbl) / 2, DECK_Y + 100);
        }

        private void drawCardBack(Graphics2D g2, int x, int y, int w, int h) {
            // Card shadow
            g2.setColor(new Color(0, 0, 0, 70));
            g2.fillRoundRect(x + 3, y + 4, w, h, 10, 10);
            // Card body
            g2.setColor(CREAM);
            g2.fillRoundRect(x, y, w, h, 10, 10);
            // Pattern
            g2.setColor(new Color(0x1A3A6B));
            g2.fillRoundRect(x + 4, y + 4, w - 8, h - 8, 7, 7);
            // Crosshatch pattern
            g2.setColor(new Color(255, 255, 255, 30));
            g2.setStroke(new BasicStroke(0.7f));
            for (int i = 0; i < w; i += 8) {
                g2.drawLine(x + 4 + i, y + 4, x + 4, y + 4 + i);
            }
            // Gold border on card
            g2.setColor(GOLD);
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawRoundRect(x, y, w, h, 10, 10);
            g2.drawRoundRect(x + 4, y + 4, w - 8, h - 8, 7, 7);
        }

        private void drawLabels(Graphics2D g2, int W, int H) {
            // Title
            g2.setFont(FONT_TITLE);
            g2.setColor(GOLD);
            String title = "♠  ROYAL POKER  ♠";
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(title, W / 2 - fm.stringWidth(title) / 2, 32);

            // Section labels
            if (!communitySprites.isEmpty()) {
                g2.setFont(FONT_LABEL);
                g2.setColor(new Color(201, 168, 76, 180));
                String lbl = "— CARTAS COMUNITARIAS —";
                fm = g2.getFontMetrics();
                g2.drawString(lbl, W / 2 - fm.stringWidth(lbl) / 2, 210);
            }
            if (!playerSprites.isEmpty()) {
                g2.setFont(FONT_LABEL);
                g2.setColor(new Color(201, 168, 76, 180));
                String lbl = "— TU MANO —";
                fm = g2.getFontMetrics();
                g2.drawString(lbl, W / 2 - fm.stringWidth(lbl) / 2, 410);
            }

            // Player name bottom-left
            if (!playerName.isEmpty()) {
                g2.setFont(FONT_LABEL);
                g2.setColor(CREAM);
                g2.drawString("Jugador: " + playerName, 55, H - 30);
            }
        }

        private void drawChipCounter(Graphics2D g2, int W, int H) {
            if (chips == 0 && displayChips == 0) return;

            // Chip icon + counter, bottom-right
            int cx = W - 200, cy = H - 55;

            // Chip stack icon
            for (int i = 3; i >= 0; i--) {
                g2.setColor(i % 2 == 0 ? GOLD : new Color(0xA07820));
                g2.fillOval(cx, cy - i * 4, 36, 14);
                g2.setColor(GOLD_DIM);
                g2.drawOval(cx, cy - i * 4, 36, 14);
            }

            // Chip count
            g2.setFont(FONT_CHIP);
            g2.setColor(GOLD_LIGHT);
            String chipStr = String.format("%,d", displayChips);
            g2.drawString(chipStr, cx + 46, cy + 10);

            g2.setFont(new Font("Serif", Font.ITALIC, 12));
            g2.setColor(new Color(201, 168, 76, 140));
            g2.drawString("fichas", cx + 46, cy + 24);
        }

        private void drawCards(Graphics2D g2) {
            long now = System.currentTimeMillis();
            for (CardSprite cs : communitySprites) cs.draw(g2, now);
            for (CardSprite cs : playerSprites)    cs.draw(g2, now);
        }

        private void drawResult(Graphics2D g2, int W, int H) {
            if (resultMessage == null) return;

            int ry = H - 22; // bottom of screen, below all cards

            // Glow backdrop
            g2.setColor(new Color(0, 0, 0, 130));
            g2.fillRoundRect(W / 2 - 220, ry - 28, 440, 48, 14, 14);
            g2.setColor(resultColor);
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawRoundRect(W / 2 - 220, ry - 28, 440, 48, 14, 14);

            g2.setFont(new Font("Serif", Font.BOLD, 22));
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(resultColor);
            g2.drawString(resultMessage, W / 2 - fm.stringWidth(resultMessage) / 2, ry + 6);
        }

        private void drawPot(Graphics2D g2, int W, int H) {
            if (pot == 0) return;
            String potStr = "BOTE: " + String.format("%,d", pot);
            g2.setFont(new Font("Serif", Font.BOLD, 16));
            FontMetrics fm = g2.getFontMetrics();
            int pw = fm.stringWidth(potStr) + 24;
            int px = W / 2 - pw / 2;
            int py = H / 2 - 16;
            g2.setColor(new Color(0, 0, 0, 120));
            g2.fillRoundRect(px, py, pw, 26, 10, 10);
            g2.setColor(GOLD);
            g2.setStroke(new BasicStroke(1f));
            g2.drawRoundRect(px, py, pw, 26, 10, 10);
            g2.setColor(GOLD_LIGHT);
            g2.drawString(potStr, px + 12, py + 18);
        }

        private void drawRoleBadges(Graphics2D g2, int W, int H) {
            if (humanRole == AIPlayer.Role.NONE && aiPlayers.isEmpty()) return;

            // Posiciones fijas para los 4 jugadores en la mesa
            // Humano abajo-centro, IAs en top-left, top-center, top-right
            String[] roleNames = { roleLabel(humanRole) };
            String humanBadge  = playerName.isEmpty() ? "Tú" : playerName;
            if (!roleNames[0].isEmpty()) humanBadge += " [" + roleNames[0] + "]";

            drawBadge(g2, humanBadge, W / 2 - 60, H - 115, playerFolded);

            int[] aiX = { 60, W / 2 - 50, W - 160 };
            int   aiY = 55;
            for (int i = 0; i < Math.min(aiPlayers.size(), 3); i++) {
                AIPlayer ai   = aiPlayers.get(i);
                String badge  = ai.getName() + " [" + roleLabel(ai.getRole()) + "]"
                              + "  " + String.format("%,d", ai.getChips());
                drawBadge(g2, badge, aiX[i], aiY, ai.isFolded());
            }
        }

        private void drawBadge(Graphics2D g2, String text, int x, int y, boolean folded) {
            g2.setFont(new Font("Monospaced", Font.PLAIN, 11));
            FontMetrics fm = g2.getFontMetrics();
            int w = fm.stringWidth(text) + 16;
            g2.setColor(folded ? new Color(80, 20, 20, 180) : new Color(0, 0, 0, 160));
            g2.fillRoundRect(x, y, w, 20, 8, 8);
            g2.setColor(folded ? new Color(180, 80, 80) : GOLD);
            g2.setStroke(new BasicStroke(1f));
            g2.drawRoundRect(x, y, w, 20, 8, 8);
            g2.setColor(folded ? new Color(180, 80, 80) : CREAM);
            g2.drawString(text, x + 8, y + 14);
        }

        private String roleLabel(AIPlayer.Role role) {
            if (role == null) return "";
            return switch (role) {
                case DEALER      -> "D";
                case SMALL_BLIND -> "SB";
                case BIG_BLIND   -> "BB";
                case NONE        -> "";
            };
        }

        private void drawActionLog(Graphics2D g2, int W, int H) {
            if (actionLog.isEmpty()) return;
            int y = H - 110;
            g2.setFont(new Font("Monospaced", Font.ITALIC, 11));
            for (int i = actionLog.size() - 1; i >= Math.max(0, actionLog.size() - 4); i--) {
                String line = actionLog.get(i);
                g2.setColor(new Color(200, 200, 200, 180));
                g2.drawString(line, W - 250, y);
                y -= 14;
            }
        }

        private void animateChips() {
            if (chipAnimStart < 0) { displayChips = chips; return; }
            long elapsed = System.currentTimeMillis() - chipAnimStart;
            long duration = 800;
            if (elapsed >= duration) {
                displayChips   = chipAnimTo;
                chips          = chipAnimTo;
                chipAnimStart  = -1;
            } else {
                float t = elapsed / (float) duration;
                t = 1 - (1 - t) * (1 - t); // ease-out quad
                displayChips = (int)(chipAnimFrom + t * (chipAnimTo - chipAnimFrom));
            }
        }
    }

    // =========================================================================
    //  CARD SPRITE — flies from deck pile to destination
    // =========================================================================
    static class CardSprite {
        final Card card;
        final int destX, destY;
        final int srcX, srcY;
        final long delay;        // ms before animation starts
        final long duration = 400;
        long startTime = -1;

        static final int W = 72, H = 100;

        CardSprite(Card card, int destX, int destY, int srcX, int srcY, long delay) {
            this.card  = card;
            this.destX = destX;
            this.destY = destY;
            this.srcX  = srcX;
            this.srcY  = srcY;
            this.delay = delay;
        }

        void draw(Graphics2D g2, long now) {
            if (startTime < 0) startTime = now;
            long elapsed = now - startTime - delay;
            if (elapsed < 0) return; // not started yet

            float t = Math.min(1f, elapsed / (float) duration);
            // ease-out cubic
            t = 1 - (float) Math.pow(1 - t, 3);

            int x = (int)(srcX + t * (destX - srcX));
            int y = (int)(srcY + t * (destY - srcY));

            // Flip effect: scale X from 0→1 after half-way
            double scaleX = t < 0.5 ? t * 2 : 1.0;

            Graphics2D cg = (Graphics2D) g2.create();
            cg.translate(x + W / 2.0, y + H / 2.0);
            cg.scale(scaleX, 1.0);
            cg.translate(-W / 2.0, -H / 2.0);
            cg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            if (t < 0.5) {
                drawBack(cg);
            } else {
                drawFace(cg);
            }
            cg.dispose();
        }

        private void drawBack(Graphics2D g2) {
            // Shadow
            g2.setColor(new Color(0, 0, 0, 60));
            g2.fillRoundRect(3, 4, W, H, 10, 10);
            g2.setColor(CREAM);
            g2.fillRoundRect(0, 0, W, H, 10, 10);
            g2.setColor(new Color(0x1A3A6B));
            g2.fillRoundRect(4, 4, W - 8, H - 8, 7, 7);
            g2.setColor(GOLD);
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawRoundRect(0, 0, W, H, 10, 10);
        }

        private void drawFace(Graphics2D g2) {
            String rank = card.getRank();
            String suit = card.getSuit();
            boolean red  = suit.equals("♥") || suit.equals("♦");
            Color textColor = red ? RED_SUIT : new Color(0x111111);

            // Shadow
            g2.setColor(new Color(0, 0, 0, 60));
            g2.fillRoundRect(3, 4, W, H, 10, 10);

            // Card face
            g2.setColor(Color.WHITE);
            g2.fillRoundRect(0, 0, W, H, 10, 10);

            // Gold border
            g2.setColor(GOLD);
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawRoundRect(0, 0, W, H, 10, 10);

            // Rank top-left
            g2.setFont(FONT_CARD_R);
            g2.setColor(textColor);
            g2.drawString(rank, 5, 22);

            // Suit top-left (small)
            g2.setFont(FONT_CARD_S);
            g2.drawString(suit, 6, 38);

            // Center suit (large)
            g2.setFont(new Font("Serif", Font.PLAIN, 36));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(suit, W / 2 - fm.stringWidth(suit) / 2, H / 2 + 14);

            // Rank bottom-right (rotated)
            Graphics2D rot = (Graphics2D) g2.create();
            rot.rotate(Math.PI, W / 2.0, H / 2.0);
            rot.setFont(FONT_CARD_R);
            rot.setColor(textColor);
            rot.drawString(rank, 5, 22);
            rot.setFont(FONT_CARD_S);
            rot.drawString(suit, 6, 38);
            rot.dispose();
        }
    }

    // =========================================================================
    //  CASINO DIALOG — replaces JOptionPane for name entry
    // =========================================================================
    static class CasinoDialog extends JDialog {

        private String result = null;
        private JTextField field;

        CasinoDialog(Frame owner, String title, String prompt) {
            super(owner, title, true);
            setUndecorated(true);
            setBackground(new Color(0, 0, 0, 0));

            JPanel root = new JPanel() {
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g;
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    // Dark card bg
                    g2.setColor(new Color(0x0D2B1A));
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
                    // Gold border
                    g2.setStroke(new BasicStroke(2f));
                    g2.setColor(GOLD);
                    g2.drawRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 20, 20);
                    // Inner border
                    g2.setColor(new Color(201, 168, 76, 60));
                    g2.drawRoundRect(6, 6, getWidth() - 12, getHeight() - 12, 16, 16);
                }
            };
            root.setOpaque(false);
            root.setLayout(new GridBagLayout());
            root.setBorder(new EmptyBorder(30, 40, 30, 40));

            GridBagConstraints gc = new GridBagConstraints();
            gc.insets = new Insets(6, 0, 6, 0);
            gc.gridx = 0; gc.fill = GridBagConstraints.HORIZONTAL;

            // Suits header
            JLabel suits = makeLabel("♠  ♥  ♦  ♣", new Font("Serif", Font.PLAIN, 22), GOLD);
            suits.setHorizontalAlignment(SwingConstants.CENTER);
            gc.gridy = 0;
            root.add(suits, gc);

            // Title
            JLabel titleLbl = makeLabel("ROYAL POKER", new Font("Serif", Font.BOLD, 26), GOLD_LIGHT);
            titleLbl.setHorizontalAlignment(SwingConstants.CENTER);
            gc.gridy = 1;
            root.add(titleLbl, gc);

            // Separator line
            JSeparator sep = new JSeparator();
            sep.setForeground(GOLD_DIM);
            sep.setBackground(GOLD_DIM);
            gc.gridy = 2;
            root.add(sep, gc);

            // Prompt
            JLabel promptLbl = makeLabel(prompt, new Font("Serif", Font.ITALIC, 15), CREAM);
            promptLbl.setHorizontalAlignment(SwingConstants.CENTER);
            gc.gridy = 3;
            root.add(promptLbl, gc);

            // Text field — custom painted
            field = new JTextField(18) {
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g;
                    g2.setColor(new Color(0x061510));
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                    super.paintComponent(g);
                }
            };
            field.setOpaque(false);
            field.setForeground(CREAM);
            field.setCaretColor(GOLD);
            field.setFont(new Font("Monospaced", Font.PLAIN, 16));
            field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(GOLD, 1, true),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)
            ));
            field.setHorizontalAlignment(SwingConstants.CENTER);
            gc.gridy = 4;
            root.add(field, gc);

            // Button
            JButton btn = new JButton("ENTRAR AL CASINO") {
                private boolean hovered = false;
                {
                    setOpaque(false);
                    setContentAreaFilled(false);
                    setBorderPainted(false);
                    setFocusPainted(false);
                    setFont(FONT_BTN);
                    setForeground(DARK_BG);
                    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    addMouseListener(new MouseAdapter() {
                        public void mouseEntered(MouseEvent e) { hovered = true; repaint(); }
                        public void mouseExited (MouseEvent e) { hovered = false; repaint(); }
                    });
                }
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g;
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    Color bg = hovered ? GOLD_LIGHT : GOLD;
                    g2.setColor(bg);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                    super.paintComponent(g);
                }
                @Override public Dimension getPreferredSize() { return new Dimension(220, 40); }
            };
            btn.addActionListener(e -> confirm());
            field.addActionListener(e -> confirm());

            JPanel btnWrap = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
            btnWrap.setOpaque(false);
            btnWrap.add(btn);
            gc.gridy = 5;
            root.add(btnWrap, gc);

            setContentPane(root);
            pack();
            setSize(380, 310);
            setLocationRelativeTo(owner);
        }

        private void confirm() {
            String text = field.getText().trim();
            if (text.isEmpty() || text.matches(".*[\\d\\W].*")) {
                field.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(RED_SUIT, 2, true),
                    BorderFactory.createEmptyBorder(6, 10, 6, 10)
                ));
                field.setText("");
                field.setToolTipText("Solo letras, sin números ni símbolos");
                return;
            }
            result = text;
            dispose();
        }

        private static JLabel makeLabel(String text, Font font, Color color) {
            JLabel l = new JLabel(text);
            l.setFont(font);
            l.setForeground(color);
            return l;
        }

        String getResult() { return result; }
    }

    // =========================================================================
    //  SOUND FX — synthesized via javax.sound (no external files needed)
    // =========================================================================
    static class SoundFX {

        static void playDeal() {
            playTone(880, 60, 0.18f);
        }

        static void playChips() {
            // Quick rattle: multiple short tones
            new Thread(() -> {
                for (int i = 0; i < 4; i++) {
                    playTone(600 + i * 120, 35, 0.12f);
                    try { Thread.sleep(45); } catch (InterruptedException ignored) {}
                }
            }).start();
        }

        static void playWin() {
            new Thread(() -> {
                int[] notes = {523, 659, 784, 1047};
                for (int note : notes) {
                    playTone(note, 100, 0.2f);
                    try { Thread.sleep(90); } catch (InterruptedException ignored) {}
                }
            }).start();
        }

        static void playTone(int hz, int durationMs, float volume) {
            try {
                AudioFormat fmt = new AudioFormat(44100, 16, 1, true, false);
                int samples = (int)(44100 * durationMs / 1000.0);
                byte[] buf   = new byte[samples * 2];
                for (int i = 0; i < samples; i++) {
                    double t     = i / 44100.0;
                    double env   = Math.min(1.0, (samples - i) / (44100.0 * 0.02)); // fade-out
                    double wave  = Math.sin(2 * Math.PI * hz * t) * env;
                    short  s     = (short)(wave * Short.MAX_VALUE * volume);
                    buf[i * 2]     = (byte)(s & 0xFF);
                    buf[i * 2 + 1] = (byte)((s >> 8) & 0xFF);
                }
                DataLine.Info info = new DataLine.Info(SourceDataLine.class, fmt);
                if (!AudioSystem.isLineSupported(info)) return;
                SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
                line.open(fmt);
                line.start();
                line.write(buf, 0, buf.length);
                line.drain();
                line.close();
            } catch (Exception ignored) {}
        }
    }

}
