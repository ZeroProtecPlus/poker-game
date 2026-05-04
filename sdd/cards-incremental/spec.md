# SDD Spec: cards-incremental

## Requisito

`dealCommunity()` debe mantener estado incremental de `communitySprites` entre fases (Flop → Turn → River).

## Escenario 1: Flop (3 cartas)

| Campo | Valor |
|-------|-------|
| **Given** | Nueva ronda de community cards (3 cartas) |
| **When** | Se llama `dealCommunity(List<Card>)` con 3 cartas |
| **Then** | `communitySprites.clear()` se ejecuta y se dibujan las 3 cartas en la mesa |

## Escenario 2: Turn (4 cartas)

| Campo | Valor |
|-------|-------|
| **Given** | 3 cartas del Flop ya dibujadas en `communitySprites` |
| **When** | Se llama `dealCommunity(List<Card>)` con 4 cartas |
| **Then** | NO se ejecuta `clear()`. Se agrega 1 carta nueva a las existentes (total 4) |

## Escenario 3: River (5 cartas)

| Campo | Valor |
|-------|-------|
| **Given** | 4 cartas (del Flop + Turn) ya dibujadas en `communitySprites` |
| **When** | Se llama `dealCommunity(List<Card>)` con 5 cartas |
| **Then** | NO se ejecuta `clear()`. Se agrega 1 carta nueva a las existentes (total 5) |

## Validación: Puntos que dependen de clear()

- `clearRoundVisualState()` (GameView.java:563-572): Llama `communitySprites.clear()` para reiniciar mano nueva — **CORRECTO**, no modificar
- `shouldClearAllRoundArtifacts` test (GameViewTest.java:52): Verifica que `clearRoundVisualState()` funciona — **SIN CAMBIOS**, testea otra función

## Decisión técnica

```java
void dealCommunity(List<Card> comm) {
    if (comm.size() <= 3) {
        communitySprites.clear();  // Flop: reiniciar desde cero
    }
    // Turn/River: agregar incrementally
    int total = comm.size();
    int startX = getWidth() / 2 - (total * 90) / 2;
    int y = 220;
    for (int i = 0; i < total; i++) {
        if (i < communitySprites.size()) continue;  // skip existentes
        communitySprites.add(new CardSprite(comm.get(i), startX + i * 90, y, DECK_X, DECK_Y, i * 150L));
    }
}
```