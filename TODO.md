# Poker Game — TODO

## ✅ Completado

### Swing → JavaFX Migration
| Item | Descripción |
|------|-------------|
| PlayerNameDialog | JavaFX con sprites overlays (menu-bg, menu_input, menu_btn_entrar). Validación solo letras. |
| CasinoDialog (Swing) | Eliminado de GameView.java (~200 líneas). Test GameViewCasinoDialogTest.java eliminado. |
| ResumeGameDialog | JavaFX modal para "Reanudar partida con X fichas". Reemplaza JOptionPane. |
| Main.java | JOptionPane.showMessageDialog → JavaFX Alert con fallback System.err. |
| GameViewSyncTest | Agregado test de Platform.runLater + CompletableFuture. |
| GameView JFrame temprano | hideFrame()/showFrame() — mesa ya no aparece durante diálogo de nombre. |

### LAN Bugfixes
| Item | Descripción |
|------|-------------|
| PendingActionRegistry race | ConcurrentHashMap + putIfAbsent atómico. Acción de red rápida ya no se pierde. |
| Disconnect freeze | collectHumanActions() verifica conexión antes de await. Desconexión → FOLD instantáneo + broadcast a clientes. |

---

## 📋 Pendientes

### LAN (Medium)
- [x] HostServer socket leak — cliente falla antes de registerClient, socket queda abierto
- [x] MessageCodec.send() — nuevo BufferedWriter por mensaje → cachear por conexión
- [x] Sin timeout en sockets — serverSocket.setSoTimeout() y socket.setSoTimeout()
- [x] LanClientController — nuevo diálogo por cada LOBBY_STATE (stacking)
- [x] humanChips solo guarda fichas del host
- [x] Lobby bloquea sin refresh en vivo de jugadores

### Migración Swing → JavaFX
- [x] **Fase 1:** GameTable shell — Stage + game-bg.png + FxMenuChrome (16 tests ✅)
- [x] **Fase 2:** Player/AI badges, pot, roles — CSS Labels posicionados (9 nuevos tests, 16 total ✅)
- [ ] **Fase 3:** Cartas (Canvas programático) + animaciones deal/flip
- [ ] **Fase 4:** Betting buttons + slider
- [ ] **Fase 5:** Chip counter, action log, result message, polish
- [x] LanDialogs — JOptionPane IP/puerto → JavaFX TextInputDialog + Alert
- [x] LanClientController — toasts JOptionPane → JavaFX Alert, SwingUtilities → Platform.runLater
- [x] LanHostController — toasts JOptionPane → JavaFX Alert, SwingUtilities → Platform.runLater

### Limpieza
- [ ] Regenerar JARs en dist/RoyalPoker/ (bytecode viejo con CasinoDialog)
