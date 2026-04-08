# PokerGame-Beta — TODO

## Leyenda
- 🔴 Crítico — bloquea funcionalidad o compilación
- 🟠 Alto — impacta correctitud del juego
- 🟡 Medio — deuda técnica o mejora importante
- 🔵 Bajo — mejoras de calidad / UX

---

## 1. Sincronización Lógica ↔ UI
**Prioridad:** 🔴 Crítico  
**Origen:** Main.java To-do #1

La lógica base de reparto ya está implementada, pero la sincronización entre el hilo del juego (background thread) y el EDT de Swing es frágil. Los estados del modelo no siempre se reflejan correctamente en la vista en el momento correcto.

**Problemas relacionados:**
- `Thread.sleep(300)` hardcodeado en el constructor de `GameView` como hack para esperar que Swing inicialice el frame. Propenso a race conditions en máquinas lentas.
- Uso de `Object.wait()` / `notifyAll()` mezclado con `SwingUtilities.invokeAndWait()` de forma manual y frágil.

---

## 2. Distribución Paso a Paso (Flop, Turn, River)
**Prioridad:** 🟠 Alto  
**Origen:** Main.java To-do #2

Las cartas comunitarias del Flop (3 cartas), Turn y River (1 carta cada una) deben mostrarse de forma animada y secuencial, con pausa entre cada carta revelada. Actualmente se muestran todas juntas sin animación coordinada por fase.

---

## 3. Base de Datos — Persistencia de Usuarios y Fichas
**Prioridad:** 🟠 Alto  
**Origen:** Main.java To-do #3

No existe ningún mecanismo de persistencia. Al cerrar la aplicación se pierden:
- El nombre del jugador
- Las fichas acumuladas
- El historial de partidas

Implementar una solución de persistencia (SQLite embebido u otras opciones) para mantener el estado entre sesiones.

---

### 3.1 Persistencia de Estado de Partida (Game State) — Preparación para LAN
**Prioridad:** 🟠 Alto  
**Origen:** Extensión de arquitectura

Además de la persistencia básica de usuario, se requiere almacenar el estado completo de la partida para permitir reanudación y soporte futuro de multijugador en red local (LAN).

**Decisión arquitectónica:**
Se adopta un modelo cliente-servidor donde un nodo (host) centraliza:
- La lógica del juego
- El estado de la partida
- La base de datos (SQLite)

Los clientes no almacenan estado persistente; únicamente envían acciones y reciben actualizaciones.

---

### Modelo de datos requerido

**Tabla: `game_state`**
- `id`
- `fase` (preflop, flop, turn, river)
- `bote`
- `jugador_actual`
- `cartas_comunitarias` (serializadas como JSON)
- `timestamp`

**Tabla: `players`**
- `id`
- `nombre`
- `chips`
- `turno` (boolean o índice)
- `estado` (activo, fold, all-in)
- `cartas` (JSON)
- `posicion` (dealer, SB, BB)

**Tabla opcional: `actions_log`**
- `id`
- `player_id`
- `accion` (bet, call, fold, etc)
- `cantidad`
- `timestamp`

---

### Requisitos técnicos

- Serializar cartas y manos como JSON (ej: `["AH","KD"]`)
- Implementar clase `GameState` que represente snapshot completo
- Implementar `GameRepository.save(GameState)` y `load()`
- Reconstrucción completa del juego desde DB (rehidratación de objetos)

---

### Flujo de reanudación

1. El host carga `GameState` desde SQLite
2. Se reconstruyen:
   - Jugadores
   - Cartas
   - Turno actual
   - Bote
3. El `GameController` restaura el flujo
4. Los clientes reciben el estado sincronizado

---

### Consideraciones importantes

- Solo el host escribe en la base de datos
- Evitar múltiples fuentes de verdad (single source of truth)
- Diseñar repositorios desacoplados para futura migración (ej: PostgreSQL o Supabase)
- Validar integridad del estado antes de cargar

---

### Futuro (no implementar aún)

- Sincronización por sockets (LAN)
- Broadcasting de estado a clientes
- Persistencia remota opcional (backend)

---

## 4. Testing — Pruebas Funcionales y No Funcionales
**Prioridad:** 🟠 Alto  
**Origen:** Main.java To-do #4

No existe ningún test en el proyecto. Considerando la complejidad del evaluador de manos (combinaciones de 7 cartas en todas las categorías hasta Royal Flush) y la lógica de apuestas, la ausencia de tests es un riesgo alto.

**Áreas prioritarias para testear:**
- Evaluador de manos (`PokerGame.evaluateCards()` y `evaluateFive()`) — todas las categorías de mano
- Lógica de `BettingRound` (canCheck, canBet, callAmount, minRaiseAmount)
- Rotación de roles (dealer, SB, BB) entre manos
- Distribución del bote y showdown

---

## 5. Modal de Confirmación "¿Continuar?"
**Prioridad:** 🟡 Medio  
**Origen:** Main.java To-do #5

El diálogo que pregunta al jugador si desea continuar la siguiente mano debe implementarse como un modal custom dentro de Swing (al estilo del `CasinoDialog` ya existente), en lugar de un `JOptionPane` estándar que rompe con la estética del juego.

---

## 6. Mejoras de Tipografía y Diseño UI
**Prioridad:** 🔵 Bajo  
**Origen:** Main.java To-do #6

Investigar y mejorar las fuentes disponibles en `javax.swing`. Actualmente se usan fuentes del sistema (Serif, Monospaced). Evaluar:
- Fuentes embebidas (cargar `.ttf` desde recursos)
- Mejoras de contraste y legibilidad en el log de acciones
- Consistencia tipográfica en toda la UI

---

## 7. Acoplamiento Circular AIPlayer → PokerGame
**Prioridad:** 🟠 Alto  
**Origen:** Análisis técnico

`AIPlayer.evaluateHandStrength()` instancia un `PokerGame` solo para invocar `evaluateCards()`. Esto genera una dependencia circular dentro del paquete `model`:

```java
// En AIPlayer.java — problemático
PokerGame tempGame = new PokerGame(null);
PokerGame.HandRank rank = tempGame.evaluateCards(all);
```

**Solución:** Extraer el evaluador a una clase utilitaria estática `HandEvaluator` que pueda ser usada tanto por `PokerGame` como por `AIPlayer` sin crear dependencias circulares.

---

## 8. Falta Interfaz Player Unificada
**Prioridad:** 🟡 Medio  
**Origen:** Análisis técnico

Las fichas del jugador humano se almacenan en `User.numbChips` (gestionado por `PokerGame`) y las de las IAs en `AIPlayer.chips`. Son dos sistemas de tracking paralelos sin abstracción compartida.

**Solución:** Crear una interfaz `Player` (o clase abstracta) que unifique `User` y `AIPlayer` con métodos comunes: `getChips()`, `setChips()`, `getName()`, `getHand()`, `hasFolded()`, etc.

---

## 9. Lógica de Empates — Split Pot
**Prioridad:** 🟠 Alto  
**Origen:** Análisis técnico

`determineWinner()` no implementa correctamente los empates. En caso de misma categoría de mano, el humano siempre gana sin comparar kickers. No existe lógica de split pot (división del bote entre dos manos idénticas).

```java
// Actual — incorrecto en empate exacto
if (bestAI == null || humanRank.ordinal() >= bestAIRank.ordinal()) {
    return null; // null = humano gana siempre en empate
}
```

---

## 10. BettingRound.canCheck() — Parámetro Ignorado
**Prioridad:** 🟡 Medio  
**Origen:** Análisis técnico

El método `canCheck(int playerCurrentBet)` recibe el bet actual del jugador pero no lo usa. El caso del Big Blind en preflop (puede hacer check si nadie subió, aunque su `currentBet > 0`) no está correctamente contemplado.

---

## 11. Loop de Apuestas Hardcodeado
**Prioridad:** 🟡 Medio  
**Origen:** Análisis técnico

El loop de rondas de apuestas tiene un límite hardcodeado de 6 iteraciones. En situaciones con muchas subidas consecutivas, la ronda podría cortarse prematuramente.

```java
int maxIterations = 6; // ← hardcoded
while (maxIterations-- > 0) { ... }
```

---

## 12. Validación de Fichas Negativas en User
**Prioridad:** 🔵 Bajo  
**Origen:** Análisis técnico

El setter `User.setNumbChips(int)` no valida que el valor sea positivo. Aunque `PokerGame` usa `Math.min()` para mitigarlo, el modelo no tiene protección propia contra fichas negativas.

---

## Resumen por Prioridad

| # | Tarea | Prioridad |
|---|---|---|
| 1 | Sincronización Lógica ↔ UI | 🔴 Crítico |
| 4 | Testing funcional y no funcional | 🟠 Alto |
| 2 | Distribución paso a paso (Flop/Turn/River) | 🟠 Alto |
| 3 | Base de datos / Persistencia | 🟠 Alto |
| 7 | Refactor — Extraer HandEvaluator | 🟠 Alto |
| 9 | Split Pot en empates | 🟠 Alto |
| 5 | Modal "¿Continuar?" custom | 🟡 Medio |
| 8 | Interfaz Player unificada | 🟡 Medio |
| 10 | BettingRound.canCheck() corregido | 🟡 Medio |
| 11 | Loop de apuestas sin límite hardcodeado | 🟡 Medio |
| 6 | Tipografía y diseño UI | 🔵 Bajo |
| 12 | Validación fichas negativas en User | 🔵 Bajo |
