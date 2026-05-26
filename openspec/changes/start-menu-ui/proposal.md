# Proposal: Menú de inicio y flujo single-player JavaFX

## Intent

Unificar la experiencia visual del juego en **1672×941** (sprites nativos): menú con hover legible, nombre de jugador en JavaFX, y base para multijugador con sprites.

## Scope

### In Scope
- Hover con sombra acotada al slot del botón (`MenuButtonSlot`)
- `PlayerNameDialog` JavaFX (reemplaza `CasinoDialog` en `getUserName`)
- Chrome minimizar/cerrar y ventanas no redimensionables
- Mesa Swing `GameView` a 1672×941

### Out of Scope
- Sprites Host/Join para multijugador (fase siguiente)
- Migrar mesa Swing a JavaFX
- Calibración automática de hit zones desde PNG

## Capabilities

### New Capabilities
- `start-menu`: menú principal con sprites y navegación Single/Multi/Settings
- `player-name-entry`: diálogo FX de nombre single-player

### Modified Capabilities
- None (sin `openspec/specs/` previos)

## Approach

`MenuButtonSlot` recorta overlay por `Rectangle2D`, sombra en placa del tamaño del botón. `PlayerNameDialog` reutiliza `Star_Game.png`, `FxMenuChrome`, `JavaFxBootstrap`. `GameController` sin cambios de contrato.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `view/fx/MenuButtonSlot.java` | New | Botón + hover |
| `view/fx/PlayerNameDialog.java` | New | Nombre FX |
| `view/fx/StartMenuDialog.java` | Modified | Usa slots |
| `view/GameView.java` | Modified | `getUserName` → FX |
| `openspec/changes/start-menu-ui/` | New | proposal + explore |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Hit zones desalineadas | Med | Constantes documentadas; ajuste manual |
| FX+Swing toolkit | Low | `JavaFxBootstrap` antes de mesa |
| Mesa 1672×941 recorta UI | Med | Probar `TablePanel`; ajustar layout si hace falta |

## Rollback Plan

Revertir commits de `view/fx/*` y `getUserName`; restaurar `showNameDialog`/`CasinoDialog`. Quitar `proposal.md` si se aborta el change.

## Dependencies

- JavaFX 21 (`pom.xml`)
- Sprites `Star_Game*.png` en `src/main/resources/sprites/`

## Success Criteria

- [ ] Hover visible alineado al botón en los 3 ítems del menú
- [ ] Single player: menú → nombre FX 1672×941 → mesa sin `CasinoDialog`
- [ ] Minimizar/cerrar en menú, nombre y ajustes
- [ ] `mvn compile` y flujo manual OK
