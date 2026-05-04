# PokerGame-Beta — TODO

## Leyenda
- 🔴 Crítico — bloquea funcionalidad o compilación
- 🟠 Alto — impacta correctitud del juego
- 🟡 Medio — deuda técnica o mejora importante
- 🔵 Bajo — mejoras de calidad / UX

---

## 1. ✅ Sincronización Lógica ↔ UI (SOLUCIONADO)
**Prioridad:** 🔴 Crítico  
**Origen:** Main.java To-do #1
**Estado:** ✅ Solucionado

La sincronización entre el hilo del juego y el EDT de Swing ahora es determinista. Se implementaron:

- **Barrera UI ready**: `CountDownLatch` completado al final de `buildFrame()` en EDT.
- **Helper seguro EDT**: `runOnEdtAndWait` y `callOnEdtAndWait` con guard `isEventDispatchThread()`.
- **Señales explícitas**: `CompletableFuture` completados desde EDT para input del jugador y fin de animaciones.
- **Timeout controlado**: 3000ms estándar con fallback CHECK/FOLD.

```java
// Uso en GameController
newGame.awaitUiReady(newGame.getUiSyncTimeoutMs());  // Espera UI antes de iniciar
action = newGame.waitForPlayerAction(round, bet, timeoutMs);  // Input con timeout
newGame.awaitLastAnimation(timeoutMs);  // Espera fin de animación
```

Se eliminó `Thread.sleep(300)` y todo `wait/notify` frágil.

---

## 2. Distribución Incremental de Cartas Comunitarias (Flop, Turn, River)
**Prioridad:** 🟠 Alto  
**Origen:** Main.java To-do #2

**Problema actual:** Cada vez que se muestra una fase (Flop→Turn→River), el método `dealCommunity()` hace `communitySprites.clear()` y redibuja TODAS las cartas desde cero. Esto causa:
- Las cartas de fases anteriores se "re-dibujan" en lugar de mantenerse en la mesa
- Animación redundante que可能出现 problemas de rendimiento
- Experiencia visual confusa (las cartas parecen "reaparecer" en lugar de sumarse)

**Comportamiento esperado:**
- Flop: mostrar 3 cartas animate dsdela mesa (nuevo)
- Turn: agregar 1 carta a las 3 existentes (incremental, no redibujar todo)
- River: agregar 1 carta a las 4 existentes (incremental)

**Solución técnica:**
- Modificar `dealCommunity()` para no hacer `clear()` en Turn/River
- Mantener estado acumulativo de `communitySprites` entre fases

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

### 3.2 Empaquetado de Base de Datos SQLite para Distribución (.exe)
**Prioridad:** 🟠 Alto  
**Origen:** Decisión de empaquetado

Al compilar el proyecto a un `.exe` (mediante `jpackage`, Launch4j o similar), la base de datos SQLite debe inicializarse correctamente en el entorno del usuario final.

**Decisión arquitectónica:**
- Incluir una base de datos SQLite **vacía o con datos iniciales** como recurso empaquetado dentro del ejecutable.
- Al iniciar la aplicación, verificar si existe una base de datos en el directorio de datos del usuario (ej. `%APPDATA%/PokerGame/` en Windows, `~/.pokergame/` en Linux/Mac).
- Si **no existe**, copiar la base de datos empaquetada al directorio de datos del usuario y luego inicializar/actualizar el esquema si es necesario.
- Si **existe**, conectar directamente a esa base de datos (persistencia entre sesiones).

**Ventajas:**
- No requiere instalador separado ni permisos de administrador.
- La base de datos del usuario persiste entre actualizaciones del ejecutable.
- Permite migraciones de esquema futuras (versionado de DB).

**Implementación técnica:**
- Clase `DatabaseBootstrapper` que maneje la copia inicial y migraciones.
- Ruta configurable: `System.getProperty("user.home") + "/.pokergame/poker.db"`.
- Uso de `getClass().getResourceAsStream()` para leer la DB empaquetada.

---

### Futuro (no implementar aún)

- Sincronización por sockets (LAN)
- Broadcasting de estado a clientes
- Persistencia remota opcional (backend)

---

## 4. ✅ Testing — Pruebas Funcionales (SOLUCIONADO)
**Prioridad original:** 🟠 Alto  
**Estado:** ✅ Solucionado  
**Origen:** Main.java To-do #4

Se implementó infraestructura de testing JUnit 5 con 44 test cases cubriendo:

- **HandEvaluator**: 19 tests (todas las categorías de mano, desempates, validaciones)
- **BettingRound**: 13 tests (canCheck, canBet, playerBets, addToPot)
- **PokerGame rol rotation**: 4 tests (dealer, SB, BB, wrap-around)
- **Pot distribution**: 8 tests (single winner, split pot, showdown, null handling)

Archivos creados en `src/test/model/`:
- `TestUtils.java` — helpers de testing
- `HandEvaluatorTest.java` (16 tests)
- `HandEvaluatorTieBreakTest.java` (3 tests)
- `BettingRoundTest.java` (7 tests)
- `BettingRoundCanCheckTest.java` (6 tests)
- `PokerGameRoleRotationTest.java` (4 tests)
- `PokerGamePotDistributionTest.java` (8 tests)
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

## 7. ✅ Acoplamiento Circular AIPlayer → PokerGame (SOLUCIONADO)
**Prioridad original:** 🟠 Alto  
**Estado:** ✅ Solucionado  
**Origen:** Análisis técnico

`AIPlayer.evaluateHandStrength()` instancia un `PokerGame` solo para invocar `evaluateCards()`. Esto genera una dependencia circular dentro del paquete `model`:

```java
// En AIPlayer.java — problemático
PokerGame tempGame = new PokerGame(null);
PokerGame.HandRank rank = tempGame.evaluateCards(all);
```

**Solución aplicada:** Se extrajo `HandEvaluator`, `PokerGame` delega evaluación con mapeo compatible y `AIPlayer` dejó de instanciar `new PokerGame(null)` para evaluar fuerza de mano.

---

## 8. ✅ Interfaz Player Unificada (SOLUCIONADO)
**Prioridad original:** 🟡 Medio  
**Estado:** ✅ Solucionado  
**Origen:** Análisis técnico

Las fichas del jugador humano se almacenan en `User.numbChips` (gestionado por `PokerGame`) y las de las IAs en `AIPlayer.chips`. Son dos sistemas de tracking paralelos sin abstracción compartida.

**Solución:** Crear una interfaz `Player` (o clase abstracta) que unifique `User` y `AIPlayer` con métodos comunes: `getChips()`, `setChips()`, `getName()`, `getHand()`, `hasFolded()`, etc.

---

## 9. ✅ Lógica de Empates — Split Pot (SOLUCIONADO)
**Prioridad original:** 🟠 Alto  
**Estado:** ✅ Solucionado  
**Origen:** Análisis técnico

Se implementó resolución de showdown con comparación completa de fuerza de mano (categoría + desempate), detección de multi-ganador en empate exacto y distribución determinística del bote (split pot), incluyendo manejo de residuo.

```java
// Actual — incorrecto en empate exacto
if (bestAI == null || humanRank.ordinal() >= bestAIRank.ordinal()) {
    return null; // null = humano gana siempre en empate
}
```

---

## 10. ✅ BettingRound.canCheck() — Parámetro Corregido (SOLUCIONADO)
**Prioridad original:** 🟡 Medio  
**Estado:** ✅ Solucionado  
**Origen:** Análisis técnico

El método `canCheck(int playerCurrentBet)` ahora considera correctamente el bet actual del jugador. Se corrige el caso del Big Blind en preflop (puede hacer check si nadie subió, aunque `currentBet > 0`) y se agregan regresiones de modelo/UI para evitar reintroducción del bug.

---

## 11. ✅ Loop de Apuestas Hardcodeado (SOLUCIONADO)
**Prioridad original:** 🟡 Medio  
**Estado:** ✅ Solucionado  
**Origen:** Análisis técnico

Se eliminó el límite hardcodeado de iteraciones en la ronda de apuestas y se reemplazó por cierre basado en reglas de estado del juego (settlement):

- La ronda termina cuando todos los jugadores activos están resueltos (`igualaron apuesta`, `all-in`, `fold` o `no pueden actuar`).
- Se mantiene cierre temprano si queda un único jugador activo.
- Se agregó guard de no-progreso por fingerprint de estado para evitar loops infinitos sin usar un cap mágico fijo.

Con esto, secuencias largas de `raise/re-raise` ya no se cortan prematuramente.

---

## 12. Validación de Fichas Negativas en User
**Prioridad:** 🔵 Bajo  
**Origen:** Análisis técnico

El setter `User.setNumbChips(int)` no valida que el valor sea positivo. Aunque `PokerGame` usa `Math.min()` para mitigarlo, el modelo no tiene protección propia contra fichas negativas.

---

## 13. ✅ Limpieza de Mesa Entre Rondas (SOLUCIONADO)

**Prioridad original:** 🟠 Alto  
**Estado:** ✅ Solucionado  
**Origen:** Bug detectado en flujo de juego  

Después de finalizar una mano (ya sea por *fold* o por *showdown*), se muestra correctamente el modal de “¿Continuar?”, pero la mesa **no se resetea visualmente de forma inmediata** al iniciar la siguiente ronda.

### Comportamiento actual
- Se reparten nuevas cartas correctamente  
- Las cartas comunitarias anteriores permanecen visibles en la mesa  
- La UI solo se actualiza correctamente cuando ocurre una acción posterior (ej: `check`, `bet`, etc.)

### Problema
Existe una desincronización entre el estado del modelo y la vista, donde:
- El modelo sí reinicia el estado de la partida  
- Pero la UI no refleja ese reset hasta que se dispara un evento adicional  

Esto genera confusión visual y puede llevar al usuario a interpretar incorrectamente el estado del juego.

### Causa probable
- Falta de un método explícito de limpieza de mesa (`clearTable()` o similar)  
- La actualización de la UI depende de eventos de acción en lugar de eventos de cambio de estado  
- No se está forzando un `repaint()` / `revalidate()` tras reiniciar la partida  

### Requisitos de solución

Implementar una limpieza completa y explícita de la mesa al iniciar una nueva mano:

#### A nivel de vista (`GameView`)
- Limpiar cartas comunitarias (flop, turn, river)  
- Limpiar cartas de jugadores (visuales)  
- Resetear labels de estado (bote, turno, acciones previas)  
- Forzar actualización:
  - `revalidate()`
  - `repaint()`

#### A nivel de controlador (`GameController`)
- Invocar método de limpieza antes de repartir nuevas cartas  
- Asegurar que el flujo sea:
  1. Limpiar UI  
  2. Resetear estado del modelo  
  3. Repartir cartas  
  4. Renderizar nuevo estado  

### Criterios de aceptación
- Al iniciar una nueva mano, la mesa debe aparecer completamente limpia **antes** de mostrar nuevas cartas  
- No debe depender de acciones del jugador para actualizarse  
- No deben persistir elementos visuales de la ronda anterior  

---

## 14. Mejora de Feedback Visual e Información de Juego (UX/UI)

**Prioridad:** 🟡 Medio  
**Origen:** Mejora de experiencia de usuario  

La interfaz actual no comunica de forma clara el estado del juego, lo que dificulta la toma de decisiones del jugador y reduce la claridad del flujo.

### Problemas actuales
- No es evidente:
  - Quién tiene el turno actual  
  - Quién realizó la última acción (`bet`, `raise`, `fold`, etc.)  
  - Cuál es la apuesta mínima o máxima  
  - Quién es el dealer, small blind (SB) y big blind (BB)  
- Información clave está ausente o poco visible  
- Falta jerarquía visual en los elementos de la UI  

### Objetivos de mejora
Proveer **feedback visual inmediato y claro** sobre el estado del juego, sin depender del log de texto.

### Requisitos funcionales

#### 1. Indicador de turno
- Resaltar visualmente al jugador activo  
- Opciones:
  - Borde iluminado  
  - Cambio de color de fondo  
  - Ícono o marcador (ej: flecha o círculo)  

#### 2. Indicador de acción reciente
- Mostrar la última acción realizada por cada jugador:
  - `CHECK`, `BET`, `CALL`, `FOLD`, `RAISE`  
- Puede mostrarse como:
  - Texto sobre el avatar  
  - Tooltip temporal  
  - Badge visual  

#### 3. Información de apuestas
Mostrar claramente:
- Apuesta mínima (`min bet`)  
- Apuesta máxima (si aplica)  
- Cantidad a pagar para hacer `call`  
- Tamaño actual del bote (`pot`)  

#### 4. Posiciones de la mesa
Identificar visualmente:
- Dealer (D)  
- Small Blind (SB)  
- Big Blind (BB)  

Esto puede implementarse mediante:
- Íconos sobre cada jugador  
- Labels pequeños junto al nombre  

#### 5. Mejora del layout informativo
- Separar visualmente:
  - Zona de jugadores  
  - Cartas comunitarias  
  - Información del juego (pot, turnos, apuestas)  
- Evitar saturación de texto  
- Priorizar información relevante en cada fase  

### Requisitos técnicos
- Evitar lógica de UI dentro del modelo (mantener separación MVC)  
- Centralizar el renderizado del estado en la vista  
- Usar eventos o bindings para actualizar UI al cambiar el estado  
- Mantener consistencia visual (colores, tipografía, espaciado)  

### Criterios de aceptación
- El jugador puede identificar el estado del juego sin leer logs  
- El turno actual es evidente en todo momento  
- Las acciones recientes son visibles de forma inmediata  
- La información de apuestas es clara y accesible  
- La UI mejora la comprensión sin sobrecargar visualmente  

---

## 15. UX de Apuesta Personalizada (BET) — Modal/Control poco usable
**Prioridad:** 🟡 Medio  
**Origen:** Feedback de uso

La interfaz para ingresar una apuesta personalizada (acción **BET/RAISE**) tiene fricción de uso: slider demasiado sensible, acceso poco claro al valor exacto y baja intuición visual.

**Problemas observados:**
- Slider con sensibilidad alta (difícil elegir montos finos)
- Falta de input numérico directo/teclado
- Jerarquía visual pobre (no se distingue bien min, actual y all-in)

**Refinamiento de solución:**
- Reemplazar o complementar slider con campo numérico editable (`JTextField/JSpinner`) + validación
- Añadir controles de incremento/decremento (±1, ±5, ±10, ±50 según ciegas)
- Mostrar claramente: **mínimo permitido**, **monto actual**, **stack restante** y **all-in**
- Mejorar layout visual del modal de apuesta para que la acción principal sea evidente

---

## 16. ✅ Flujo automático tras Fold del humano en modo 1 vs CPU (SOLUCIONADO)
**Prioridad original:** 🟠 Alto  
**Estado:** ✅ Solucionado  
**Origen:** Feedback de juego

En modo 1 vs CPU, cuando el jugador humano hace **fold**, el sistema no siempre continúa automáticamente. En algunos casos queda esperando interacción del usuario o incluso permite acciones inválidas (apostar estando foldeado).

**Comportamiento esperado (regla):**
- Si el humano está `folded`, su turno debe saltarse automáticamente
- El motor debe continuar la mano/resolver fase sin solicitar acciones al humano foldeado
- La UI debe deshabilitar acciones de apuesta/call/raise para jugador foldeado

**Refinamiento de solución:**
- Corregir guardas de estado en `GameController`/flujo de ronda para no bloquear en espera de input humano foldeado
- Enforzar validación de acciones por estado del jugador (`folded` => sin acciones de apuesta)
- Añadir tests de regresión para escenario: humano foldea temprano y la mano continúa hasta resolución automática

---

## 17. ✅ Validación de Nombres de Jugadores desde el Host (LAN) (SOLUCIONADO)

**Prioridad original:** 🟠 Alto  
**Estado:** ✅ Solucionado  
**Origen:** Requisito para multijugador en red (LAN)

En partidas multijugador bajo arquitectura cliente-servidor, el **host debe actuar como autoridad central** para validar los nombres de los jugadores antes de permitir su ingreso a la partida.

Actualmente existen reglas locales para restringir caracteres inválidos, pero **no se controla la unicidad ni coherencia global de los nombres**, lo que puede generar conflictos en la identificación de jugadores durante la partida.

---

### Problemas actuales
- Posibilidad de nombres duplicados entre jugadores  
- Falta de validación centralizada (cada cliente valida de forma aislada)  
- Riesgo de ambigüedad en:
  - Logs de acciones  
  - Identificación de turnos  
  - Persistencia de datos  
- No hay feedback claro al cliente cuando un nombre es rechazado  

---

### Objetivos
- Garantizar que cada jugador tenga un **nombre único dentro de la sesión**  
- Centralizar la validación en el host (single source of truth)  
- Asegurar consistencia en UI, logs y persistencia  

---

### Requisitos funcionales

#### 1. Validación de unicidad
- El host debe rechazar nombres ya registrados en la partida activa  
- Comparación *case-insensitive* (ej: `Juan` == `juan`)  

#### 2. Validación de formato
- Reutilizar reglas existentes:
  - Sin caracteres especiales no permitidos  
  - Longitud mínima y máxima definida  
- Validación final siempre ocurre en el host (no confiar en el cliente)  

#### 3. Protocolo de conexión
- Flujo esperado:
  1. Cliente propone nombre  
  2. Host valida (unicidad + formato)  
  3. Host responde:
     - ✅ Aceptado → jugador se une  
     - ❌ Rechazado → se envía motivo  

#### 4. Feedback al usuario
- Mostrar mensajes claros en cliente:
  - “Nombre ya en uso”  
  - “Formato inválido”  
- Permitir reintento sin reiniciar conexión  

---

### Requisitos técnicos
- Mantener un registro en memoria en el host:
  - `Set<String>` normalizado (ej: lowercase)  
- Implementar método:
  - `boolean isNameAvailable(String name)`  
- Separar lógica de validación en un componente reutilizable (`NameValidator`)  
- Preparar integración futura con persistencia (evitar colisiones con usuarios guardados)

---

### Consideraciones
- El nombre no debe ser el identificador único interno (usar `playerId`)  
- Evitar condiciones de carrera en conexiones simultáneas  
- Validar nuevamente al reconectar clientes  

---

### Criterios de aceptación
- No pueden existir nombres duplicados en una partida  
- Todos los nombres visibles son consistentes en UI y logs  
- El sistema maneja correctamente rechazos sin romper el flujo de conexión  

---

## 18. ✅ Error preflop fold de IAs tras bet (SOLUCIONADO)
**Prioridad original:** 🟠 Alto  
**Estado:** ✅ Solucionado  
**Origen:** Bug detectado en QA manual

El bug fue corregido: cuando todos los jugadores IA foldean, el flujo ya no llama a endRound(). El mtodo allFolded() ya adjudicaba el pot directamente - slo se removi la llamada innecesaria a endRound().

---

## 19. ✅ La IA no puede evaluar en preflop cuando no hay cartas comunitarias (SOLUCIONADO)
**Prioridad original:** 🟠 Alto  
**Estado:** ✅ Solucionado  
**Origen:** Bug detectado en testing

El bug fue corregido: la IA ahora evalúa su mano directamente con hole cards cuando comunidad está vacía/nula, sin depender del board para tomar decisiones.

### Nota de calibración futura (thresholds)
Los umbrales actuales de decisión IA en preflop podrían requerir ajustefino para mejorar el comportamiento estratégico:
- Gates de call/raise/fold basados en `potOdds` + margen
- RNG de bluff ocasional (puede causar folds no determinísticos en casos bordes)
- Esto es tuning de IA, no es un bug funcional

### Problema original (ya resuelto)
- La lógica de decisión de la IA solicitaba `communityCards` para calcular odds.
- En preflop no había cartas comunitarias (arreglo vacío).
- Cuando detectaba comunidad vacía o nula, la IA no podía decidir y se retirba (`fold`).

### Comportamiento esperado (ya implementado)
- En preflop, las IAs evaluan su mano solo con sus 2 cartas privadas.
- Comparan esa fuerza directamente frente a la apuesta del humano.
- Toman una decisión basada solo en su mano (sin probabilidades del board).

---

## 20. Indicadores de turno (BB, SB, Dealer) no visibles
**Prioridad:** 🟡 Medio  
**Origen:** Feedback de usuario

El jugador no puede identificar visualmente quién es el Dealer, Small Blind (SB) y Big Blind (BB) en la mesa.

**Problema:** No hay etiquetas visuales que muestren D, SB, BB en cada jugador.

**Solución:** Agregar indicadores visuales (D, SB, BB) junto a cada jugador y mantener rotación correcta de roles.

---

## 21. ✅ Modal "Continuar?" no cierra el juego al responder "No" (SOLUCIONADO)
**Prioridad original:** 🟡 Medio  
**Estado:** ✅ Solucionado  
**Origen:** Feedback de usuario

Cuando aparece el modal preguntando "¿Continuar?" después de una mano, al seleccionar "No" el juego ahora se cierra completamente mediante un apagado controlado (graceful shutdown).

**Solución implementada:**
- Se implementó `requestGracefulShutdown()` en `GameView` con protección idempotente y seguridad de hilo EDT.
- El flujo del modal "Continuar?" ahora enruta la respuesta "No" a este método de apagado.
- La respuesta "Sí" preserva el comportamiento original sin cambios.

---

## 22. Mejora de Gráficos y Sprites de la Mesa
**Prioridad:** 🟡 Medio  
**Origen:** Feedback de usuario

El fondo de la mesa de poker y los iconos/sprites actuales son de baja calidad o genéricos. Se requiere mejorar la presentación visual para una experiencia más inmersiva.

**Problemas identificados:**
- Fondo de la mesa poco atractivo o genérico
- Iconos de cartas, fichas, botones de acciones de baja resolución
- Falta de consistencia visual con el estilo de casino

**Objetivos:**
- Reemplazar el fondo de la mesa por una imagen de mayor calidad (ej: felt verde con textura, madera, etc.)
- Actualizar iconos/sprites de:
  - Cartas (baraja completa con mejor diseño)
  - Fichas de apuestas (distintas denominaciones)
  - Botones de acciones (Check, Bet, Fold, Call, Raise)
  - Indicadores de posición (Dealer, SB, BB)
- Mantener coherencia visual y rendimiento en Swing

**Consideraciones técnicas:**
- Usar imágenes en formatos eficientes (PNG con transparencia)
- Considerar resolución 2x para pantallas retina si es necesario
- Evitar imágenes demasiado pesadas que afecten el rendimiento
- Mantener el diseño responsivo dentro del canvas de Swing

## Resumen por Prioridad

| # | Tarea | Prioridad |
|---|---|---|
| 1 | ✅ Sincronización Lógica ↔ UI (solucionado) | ✅ Completado |
| 4 | ✅ Testing funcional (solucionado) | ✅ Completado |
| 2 | ✅ Distribución incremental de cartas comunitarias (solucionado) | ✅ Completado |
| 3 | ✅ Base de datos / Persistencia (solucionado) | ✅ Completado |
| 7 | ✅ Refactor — Extraer HandEvaluator (solucionado) | ✅ Completado |
| 9 | ✅ Split Pot en empates | ✅ Completado |
| 5 | Modal "¿Continuar?" custom | 🟡 Medio |
| 8 | ✅ Interfaz Player unificada (solucionado) | ✅ Completado |
| 10 | ✅ BettingRound.canCheck() corregido | ✅ Completado |
| 11 | ✅ Loop de apuestas sin límite hardcodeado (solucionado) | ✅ Completado |
| 6 | Tipografía y diseño UI | 🔵 Bajo |
| 12 | Validación fichas negativas en User | 🔵 Bajo |
| 13 | ✅ Limpieza de mesa entre rondas (solucionado) | ✅ Completado |
| 14 | Mejora de feedback visual e información de juego | 🟡 Medio |
| 15 | UX de apuesta personalizada (BET) | 🟡 Medio |
| 16 | ✅ Auto-continuación tras fold humano (1 vs CPU) | ✅ Completado |
| 17 | ✅ Validación de Nombres de Jugadores desde el Host (LAN) (solucionado) | ✅ Completado |
| 18 | ✅ Error preflop fold de IAs tras bet (solucionado) | ✅ Completado |
| 19 | ✅ La IA no puede evaluar en preflop sin comunidad (solucionado) | ✅ Completado |
| 20 | Indicadores de turno (BB, SB, Dealer) no visibles | 🟡 Medio |
| 21 | ✅ Modal "Continuar?" no cierra el juego al responder "No" (solucionado) | ✅ Completado |
| 22 | Mejora de gráficos y sprites de la mesa | 🟡 Medio |
| 23 | Deuda técnica — Persistencia (warnings) | 🔵 Bajo |

---

## 23. Deuda Técnica — Persistencia (Warnings Post-Verificación)
**Prioridad:** 🔵 Bajo  
**Origen:** Verificación SDD `db-persistence` (Issue #3)

Items identificados durante la verificación del cambio #3 que no bloquean funcionalidad pero deben corregirse en iteraciones futuras.

### Warnings encontrados

#### 23.1 Bootstrap mechanism deviation
**Descripción:** El spec decía "copiar db/schema.sql desde classpath a user.home", pero la implementación crea el archivo SQLite vía JDBC y ejecuta migraciones.  
**Impacto:** Funcionalmente idéntico, pero diferente al diseño especificado.  
**Acción:** Unificar con el approach de copia desde recurso empaquetado para compatibilidad con distribución .exe.

#### 23.2 Falta métodos de serialización de Deck
**Descripción:** El spec C3 requiere `CardSerializer.deckToJson(Deck)` y `deckFromJson(String)`, no implementados.  
**Impacto:** No se usa actualmente (el mazo se persiste como lista de cartas).  
**Acción:** Agregar métodos cuando se necesite serializar el estado completo del mazo.

#### 23.3 UUID generation en PlayerRepository.create()
**Descripción:** El spec C4 dice que `create()` DEBE generar UUID si `playerId` es null. Actualmente se genera en `User`/`AIPlayer`.  
**Impacto:** Bajo — los constructores de entidades ya generan UUID.  
**Acción:** Mover la lógica de generación al repositorio para cumplir con el contrato.

#### 23.4 Tabla actions_log sin uso
**Descripción:** El spec C5 dice que `save()` DEBE escribir en `actions_log` atómicamente. La tabla existe pero nunca se escribe.  
**Impacto:** Funcionalidad de log de acciones no disponible.  
**Acción:** Implementar escritura de acciones o eliminar la tabla si no se va a usar.

#### 23.5 Jerarquía de excepciones simplificada
**Descripción:** El diseño especifica `EntityNotFoundException extends RepositoryException`, pero la implementación extiende `PersistenceException` directamente.  
**Impacto:** Bajo — no afecta el manejo de errores actual.  
**Acción:** Completar la jerarquía: `PersistenceException → RepositoryException → EntityNotFoundException`.

### Criterios de cierre
- [ ] Todos los warnings resueltos o convertidos en issues individuales
- [ ] Tests actualizados para reflejar comportamiento corregido
- [ ] Documentación actualizada

---

## Futuras Features (Backlog)

Funcionalidades identificadas como fuera de alcance para el ciclo actual, pero planificadas para futuras iteraciones.

### F-1. ORM / Mapeo Objeto-Relacional
**Prioridad:** 🔵 Bajo  
**Origen:** Propuesta de arquitectura #3

Evaluar migración de DAOs plain-JDBC a un ORM (Hibernate/JPA) si la complejidad de las entidades crece significativamente.

**Criterio de activación:**
- Más de 10 tablas con relaciones complejas
- Necesidad de queries dinámicas o criteria API
- Equipo con experiencia en JPA

---

### F-2. Cambios de Reglas del Juego
**Prioridad:** 🔵 Bajo  
**Origen:** Propuesta de arquitectura #3

Soporte para variantes de poker (Omaha, Texas Hold'em con diferentes estructuras de apuestas, torneos con ciegas crecientes, etc.). Requiere abstraer las reglas actuales en un motor configurable.

**Dependencias:**
- Motor de reglas desacoplado del controlador
- Configuración de variantes en base de datos

---

### F-3. Sincronización LAN en Tiempo Real
**Prioridad:** 🟡 Medio  
**Origen:** TODO #3.1 / Arquitectura

Implementar comunicación por sockets para partidas multijugador en red local (LAN). El host centraliza estado y los clientes reciben actualizaciones en tiempo real.

**Dependencias:**
- Issue #3 (persistencia) completada
- Protocolo de mensajes definido
- Manejo de reconexiones y desconexiones

---

### F-4. Broadcasting de Estado a Clientes
**Prioridad:** 🟡 Medio  
**Origen:** TODO #3.1 / Arquitectura

Mecanismo de pub/sub para sincronizar estado del juego entre host y clientes LAN. Optimizar para latencia baja y consistencia eventual.

**Dependencias:**
- F-3 (sincronización LAN)

---

### F-5. Persistencia Remota (Backend Cloud)
**Prioridad:** 🔵 Bajo  
**Origen:** TODO #3.1 / Arquitectura

Migrar la base de datos SQLite local a un backend remoto (PostgreSQL, Supabase, Firebase) para soporte de multijugador online y rankings globales.

**Criterio de activación:**
- MVP de juego local validado
- Infraestructura de backend definida
- Modelo de negocio claro (free-to-play, premium, etc.)
