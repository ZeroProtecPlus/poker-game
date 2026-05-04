# Proposal: sound-animation-sync

## Intent

El sonido de dealt cards (`SoundFX.playDeal()`) se ejecuta inmediatamente al invocar `showCommunityCards()`, pero las cartas tienen delay de ~150ms por su animación. El usuario ve las cartas aparecer 150ms después de escuchar el sonido, causando desincronización visual-auditiva.

## Scope

### In Scope
- Modificar `showCommunityCards()` en `GameView.java` para ejecutar `SoundFX.playDeal()` con delay de 150ms
- Usar `Timer` de Java Swing: `new Timer(150, e -> SoundFX.playDeal()).start()`

### Out of Scope
- Cambios en otros efectos de sonido
- Modificación de la animación de las cartas

## Capabilities

> Esta propuesta NO introduce nuevas capabilities ni modifica requerimientos existentes. Es un fix de sincronización pura.

- **None**: Este cambio no afecta el contract de specs.

## Approach

Usar `Timer` de Swing para ejecutar el sonido con delay de 150ms, sincronizándolo con el momento en que la primera carta aparece en pantalla.

```java
new Timer(150, e -> SoundFX.playDeal()).start();
```

El `Timer` se ejecuta una sola vez (no-repeat) y ejecuta el callback luego de 150ms.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `src/main/java/com/poker/ui/GameView.java` | Modified | Agregar delay al sonido en `showCommunityCards()` |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Timer no se limpia y causa memory leak | Low | El Timer de Swing es lightweight; el callback se ejecuta una vez y el Timer se dispositiona automáticamente |

## Rollback Plan

Revertir el cambio en `GameView.java`, quitando el `new Timer(...)` y dejando `SoundFX.playDeal()` directo.

## Dependencies

- Ninguna dependencia externa.

## Success Criteria

- [ ] El sonido de dealt cards se escucha ~150ms después de llamar `showCommunityCards()`
- [ ] La primera carta aparece visualmente al mismo tiempo que el sonido