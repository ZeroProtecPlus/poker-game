# Spec: table-bg-image

## Requirements

### REQ-1: Image path constant

The system **SHALL** define a constant `TABLE_BG_IMAGE_PATH` with value `"/resources/table-bg.png"` in `TablePanel` (inner class of `GameView`).

### REQ-2: Single-load image caching

When `TablePanel` is instantiated, it **SHALL** attempt to load the background image from `TABLE_BG_IMAGE_PATH` **once** and store it as an instance field (`BufferedImage`). The image **SHALL NOT** be reloaded on subsequent `paintComponent()` calls.

### REQ-3: Conditional rendering — image exists

If the background image loads successfully, `paintComponent()` **SHALL** draw it using `drawImage()` with **scale-to-fill** behavior: the image is scaled to cover the entire panel bounds while preserving aspect ratio, potentially cropping edges.

The image **SHALL** be drawn as the base layer, before any other painting (felt table, cards, labels, etc.).

### REQ-4: Conditional rendering — image missing (fallback)

If the image does **not** exist at the configured path, or fails to load for any reason, rendering **SHALL** fall back to the current behavior:
1. `drawBackground(g2, W, H)` — gradient + diagonal texture
2. `drawFeltTable(g2, W, H)` — oval felt with gold border

This fallback **MUST** be byte-identical to the pre-change behavior.

### REQ-5: Silent loading

Image loading **SHALL NOT** produce any visible errors, stack traces, or console output if the image is missing or fails to load. Exceptions **SHALL** be caught and silently ignored.

## Scenarios

### Scenario 1: Image present at startup

**GIVEN** a valid PNG image exists at `/resources/table-bg.png` relative to the working directory  
**WHEN** `GameView` is constructed (which creates `TablePanel`)  
**THEN** the image is loaded once into a `BufferedImage` field  
**AND** every `paintComponent()` call draws the image scaled-to-fill behind all other elements

### Scenario 2: Image absent at startup

**GIVEN** no file exists at `/resources/table-bg.png`  
**WHEN** `GamePanel` is constructed  
**THEN** the image field remains `null`  
**AND** every `paintComponent()` call uses `drawBackground()` + `drawFeltTable()` (current behavior)

### Scenario 3: Image corrupted / invalid format

**GIVEN** a file exists at `/resources/table-bg.png` but is not a valid image  
**WHEN** `TablePanel` is constructed  
**THEN** the exception is silently caught  
**AND** the image field remains `null`  
**AND** fallback rendering is used

### Scenario 4: Window resize with image

**GIVEN** a valid background image is loaded  
**WHEN** the window is resized  
**THEN** the image is re-scaled to fill the new panel dimensions in the next `paintComponent()` call  
**AND** the original `BufferedImage` is reused (no re-load from disk)

## Technical Notes

- Scale-to-fill: calculate scale factor as `max(panelWidth / imageWidth, panelHeight / imageHeight)`, then draw centered so the shorter dimension may crop.
- Image loading uses `ImageIO.read()` wrapped in a try-catch during `TablePanel` constructor.
- The constant path `"/resources/table-bg.png"` is resolved relative to the current working directory, not the classpath.
- No changes to `drawBackground()` or `drawFeltTable()` — they remain untouched for the fallback path.
