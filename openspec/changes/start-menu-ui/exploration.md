# Exploration: Menú de inicio y flujo multijugador (sprites nativos)

## Current State

- **Entrada:** `Main` → `StartMenuDialog` (JavaFX 1672×941, `Star_Game.png` + capas de botón).
- **Single player:** `GameController` → `GameView` (Swing) + `CasinoDialog` para nombre; mesa ahora **1672×941** fija (`MenuLayoutConstants`).
- **Multiplayer:** submenú JavaFX con botones de texto sobre el mismo fondo; LAN vía `LanHostController` / `LanClientController` y `LanDialogs` (JOptionPane).
- **Settings:** `GameSettings` en `~/.pokergame/settings.properties` (puerto, IP cliente, timeout UI).
- **Hover menú:** capas PNG siempre visibles; sombra `DropShadow` solo en zona de clic (`FxMenuChrome.bindHoverShadow`).
- **Chrome:** barra superior minimizar/cerrar en menús JavaFX (`FxMenuChrome`); stages no redimensionables.

## Affected Areas

- `src/main/java/view/fx/StartMenuDialog.java` — menú principal, hit zones, capas botón
- `src/main/java/view/fx/FxMenuChrome.java` — chrome y hover
- `src/main/java/view/fx/MultiplayerMenuDialog.java` — placeholder Host/Join
- `src/main/java/view/GameView.java` — resolución mesa single player
- `src/main/java/view/GameView.java` (`showNameDialog` / `CasinoDialog`) — paso 2 nombre aún Swing 1050×700 lógica distinta
- `src/main/resources/sprites/` — arte 1672×941 por botón
- `docs/lan-multiplayer.md` — contrato LAN

## Approaches

1. **Multijugador con sprites dedicados (como menú principal)**
   - Pros: coherencia visual; sin escala; hit zones + hover sombra reutilizando `FxMenuChrome`
   - Cons: requiere PNGs `Star_Game_host.png`, `Star_Game_join.png`, `Star_Game_back.png` (o layout en un solo sheet)
   - Effort: Medium

2. **Multijugador: mantener panel JavaFX actual**
   - Pros: ya funcional para Host/Join; poco código
   - Cons: no coincide con estética sprite del menú
   - Effort: Low

3. **Nombre jugador en JavaFX 1672×941**
   - Pros: misma resolución que menú y mesa
   - Cons: duplicar patrón `CasinoDialog` o migrar `CasinoDialog` a FX
   - Effort: Medium

4. **Un solo `MenuNavigator` + enum de pantallas**
   - Pros: chrome/hover/DIMENSIONS en un solo lugar
   - Cons: refactor más amplio
   - Effort: High

## Recommendation

**Fase inmediata (hecha):** hover sombra + botones siempre visibles + chrome + mesa 1672×941.

**Siguiente:** Approach **1** para multijugador cuando existan sprites Host/Join/Back; calibrar `BUTTON_LEFT` / `SINGLE_TOP` en `StartMenuDialog` si los clics no alinean.

**Opcional:** Approach **3** si el salto visual del diálogo de nombre molesta.

## Risks

- Tres overlays PNG a pantalla completa apilados pueden ocultarse mutuamente si no tienen transparencia fuera del botón.
- `Stage.initModality` + `showAndWait` encadena ventanas; orden con Swing `GameView` debe seguir en hilo de juego separado.
- Mesa 1672×941 puede recortar UI Swing diseñada para ~1050×700 (revisar `TablePanel` layout).

## Ready for Proposal

**Yes** — proponer change `start-menu-ui` con: (a) calibración hit zones, (b) sprites multijugador, (c) prueba layout mesa a resolución nativa.
