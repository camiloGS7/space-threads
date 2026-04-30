# Space Threads — Galaga en Java

Juego de naves estilo Galaga donde cada entidad (jugador, enemigos, proyectiles) corre en su propio hilo independiente. Desarrollado con Swing/AWT como ejercicio de patrones de concurrencia.

## Características

- **Formación de 24 enemigos** (4 filas × 6 columnas) que oscila horizontalmente y desciende hacia el jugador
- **Dive bombing** — los enemigos se desprenden de la formación y atacan en picada con trayectoria sinusoidal
- **Disparos enemigos** — los enemigos lanzan proyectiles hacia el jugador
- **3 tipos de enemigos** con distintos colores y puntajes (jefe dorado, naranja, estándar)
- **Sistema de vidas** (3 vidas) con parpadeo al recibir daño
- **Oleadas progresivas** — cada wave aumenta la velocidad, frecuencia de picadas y cadencia de disparo
- **Estrellas animadas** con efecto parallax
- **Pantalla de Game Over** con puntaje final y opción de reinicio

## Controles

| Tecla | Acción |
|---|---|
| `A` / `←` | Mover izquierda |
| `D` / `→` | Mover derecha |
| `ESPACIO` | Disparar |
| `ENTER` / `ESPACIO` | Reiniciar (Game Over) |

## Puntaje

| Enemigo | Puntos |
|---|---|
| Jefe (fila 1) | 300 pts |
| Medio (fila 2) | 200 pts |
| Estándar (filas 3-4) | 100 pts |

## Estructura del proyecto

```
Videojuego/src/
├── Main.java              # Punto de entrada, crea la ventana
├── Nave.java              # Clase abstracta base (extiende Thread)
├── MiNave.java            # Nave del jugador
├── NaveEnemiga.java       # Enemigo con estados FORMATION / DIVING
├── Proyectil.java         # Bala del jugador (Runnable)
├── ProyectilEnemigo.java  # Bala enemiga (Runnable)
└── GamePanel.java         # Panel principal, lógica del juego y rendering
```

## Modelo de concurrencia

Cada entidad del juego vive en su propio hilo:

- `MiNave` extiende `Thread` — gestiona el movimiento continuo del jugador
- `NaveEnemiga` extiende `Thread` — ejecuta la lógica de formación y picada
- `Proyectil` implementa `Runnable` — mueve la bala y detecta colisiones
- `ProyectilEnemigo` implementa `Runnable` — mueve la bala enemiga y detecta impacto en el jugador
- El **game loop** corre en un hilo separado a ~60 fps (repaint + lógica de formación)

La sincronización se maneja con campos `volatile`, bloques `synchronized` sobre las listas compartidas y el modelo de visibilidad del JMM.

## Requisitos

- Java 8 o superior
- No requiere dependencias externas

## Compilar y ejecutar

```bash
# Compilar
javac -d Videojuego/out Videojuego/src/*.java

# Ejecutar
java -cp Videojuego/out Main
```

## Curso

Taller de Programación 2026 — 261
