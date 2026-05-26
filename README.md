![PokerMao Header](docs/banner.png)

# PokerMao

**PokerMao** es una implementación completa de **Texas Hold'em Poker** desarrollada en Java, con interfaz gráfica rica, inteligencia artificial para oponentes, multijugador por LAN y una identidad visual única inspirada en la estética soviética-retro.

> Un juego donde la estrategia, el farol y la suerte se encuentran en cada mano.

---

## Características Principales

- **Texas Hold'em completo**: Pre-flop, flop, turn, river y showdown con evaluación automática de manos.
- **Modo un jugador**: Enfrentate hasta 5 oponentes controlados por IA con distintos estilos de juego.
- **Multijugador LAN**: Crea o únete a partidas en red local (host / cliente) con sincronización en tiempo real.
- **Persistencia de perfiles**: Sistema de perfiles de jugador con SQLite; tus fichas y progreso se guardan entre sesiones.
- **UI polida**: Interfaz híbrida Swing + JavaFX con animaciones, sprites personalizados y transiciones fluidas.
- **Banda sonora original**: Tres discos de vinilo conceptuales que dan identidad sonora al juego.

---

## Tecnologías

| Capa | Tecnología |
|------|------------|
| Lenguaje | Java 17 (LTS) |
| Build | Maven 3 |
| UI Principal | Swing |
| Menús y Diálogos | JavaFX 21 |
| Base de Datos | SQLite (JDBC) |
| Testing | JUnit 5, Mockito |
| Serialización | org.json |
| Empaquetado | Maven Shade Plugin (fat JAR) |

---

## Banda Sonora Original

El juego cuenta con una identidad sonora representada en tres portadas de vinilo conceptuales que evocan la estética propagandística, steampunk y soviética del universo **PokerMao**:

| Disco | Título | Concepto |
|-------|--------|----------|
| **Disk 1** | *The House Does Not Gamble* | La casa siempre gana. Portada industrial con ruleta, engranajes y arquitectura clásica. |
| **Disk 2** | *A Kopek For The Crown* | La apuesta mínima por la gloria máxima. Estrella roja ornamental sobre fondo oscuro. |
| **Disk 3** | *The Lucky General* | Estrategia y fortuna en el campo de batalla del póker. Águila bicéfala estilizada con tanques y ruleta. |

> Cada disco encapsula un estado de ánimo del juego: la tensión del casino, la ambición de la victoria y el caos controlado de la batalla final.

---

## Arquitectura

```
src/main/java/
├── Main.java              # Punto de entrada y menú principal
├── config/                # Configuración global del juego
├── controller/            # Controladores de partida (single, LAN host, LAN client)
├── model/                 # Dominio: cartas, mazo, jugadores, apuestas, evaluador de manos
│   ├── persistence/       # Acceso a datos SQLite (perfiles, partidas)
│   └── dto/               # Objetos de transferencia para red
└── view/                  # Capa de presentación Swing + JavaFX
    └── fx/                # Componentes JavaFX (menús, diálogos, tablero)

src/test/java/             # Tests unitarios y de contrato
src/main/resources/        # Sprites, fondos, CSS y portadas de discos
```

---

## Cómo Ejecutar

### Requisitos

- JDK 17 o superior
- Maven 3.8+

### Compilar y correr

```bash
# Compilar
mvn clean compile

# Ejecutar desde Maven
mvn javafx:run

# O empaquetar como fat JAR y ejecutar directamente
mvn clean package
java -jar target/poker-game-1.0.0-SNAPSHOT.jar
```

### Perfiles Maven útiles

| Perfil | Descripción |
|--------|-------------|
| `start-menu` | Menú de inicio aislado |
| `continue-dialog` | Diálogo de continuación aislado |
| `game-table` | Tablero de juego aislado (demo visual) |
| `fx-test` | Tests de JavaFX |

---

## Tests

```bash
# Ejecutar suite completa
mvn test

# Tests de contrato (Player, PokerGame)
mvn test -Dtest=PlayerContractTest,PokerGameTest

# Tests de UI (JavaFX)
mvn test -Dtest=GameTableTest
```

---

## Multijugador LAN

1. Selecciona **Multijugador** en el menú principal.
2. Como **Host**: indica tu nombre y espera conexiones en el puerto configurado.
3. Como **Cliente**: ingresa la IP del host y conectate.
4. El host sincroniza el estado del juego (cartas comunitarias, bote, fichas, log de acciones) en tiempo real.

---

## Créditos y Licencia

- **Arte de portadas**: *The House Does Not Gamble*, *A Kopek For The Crown*, *The Lucky General* — banda sonora conceptual original de PokerMao.
- **Stack**: Java, JavaFX, SQLite, JUnit, Mockito.
- Licencia: [MIT](LICENSE)

---

> *"La casa no juega, pero vos sí. Que la suerte te acompañe, camarada."*
