# Skate en Minecraft (cliente) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans (the user chose inline execution). Steps use checkbox (`- [ ]`) syntax.

**Goal:** Que el jugador patine en Minecraft con el motor de Skate 3 en modo anfitrión (hito M1, luego M2 y M3).

**Spec:** `docs/superpowers/specs/2026-10-11-skate-minecraft-client-design.md`

**Tech Stack:** Java 21, Fabric (1.21.1), JUnit 5, GLFW (gamepad), UDP. El motor y su parche están en `skate-host/`.

## Global Constraints
- Nada del motor ni de Skate 3 entra al repositorio; el cliente solo habla UDP con 127.0.0.1:34600.
- El modo solo existe con un mando; el mod base no cambia si el motor no corre.
- Cada parte pura lleva tests antes del código; las partes de Minecraft se verifican en juego.

## Tareas (M1)
1. **SkateProtocol** (`dev.cjmc.skate`): codificar entrada, mundo, borrado y teletransporte; decodificar la pose. Tests con bytes dorados (`protocol/golden/skate_*.bin`, generados por `tools/make_skate_golden.py` con las funciones de `skate_host_probe.py`).
2. **SkateInput**: mando → 18 acciones. Tests.
3. **BlockQuads**: caras expuestas con orientación correcta y partición en regiones. Tests.
4. **Ítem `skateboard`** (registro, receta, modelo, nombre) y **SkateRide** (enlace UDP, activar y desactivar, mover al jugador, bloquear los controles vanilla, avisos).
5. **SkateWorld**: secciones, presupuesto por tick, cambios de bloques, borrado de lo lejano.
6. **Verificación en juego** con el usuario y documentación (`docs/skate-client-results.md`).

## M2 y M3
Se planean al cerrar M1 (reasignado de huesos con una herramienta que exporta el esqueleto de `skater.glb`, modelo del tablero, integración con el renderizador; luego `start_games.sh` y el empaquetado).
