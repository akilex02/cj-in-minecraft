# Skate con CJ (extra opcional)

Este pack añade el **modo skate** al mod de CJ en Minecraft: CJ patina con la física, los trucos y la tabla de **Skate 3**, controlado con un mando.
Usa el motor de Skate 3 en Rust (ver `FUENTE.md`) y **tu propia copia de Skate 3**: no se incluye nada del juego.

## Qué necesitas

- El **mod de CJ ya instalado** (el paquete `cj-mod`, con su `LEEME.md`).
- **Skate 3 para Xbox 360**: el `default.xex` con su carpeta `data` al lado (el juego extraído), o la ISO del disco (solo en Windows).
- **Un mando** (Xbox, PlayStation u otro compatible).
- En **Linux**: `numpy` y `Pillow` para Python (`pip install numpy pillow`).

## Dónde poner las carpetas

Descomprime el paquete del mod como **`cj-mod`** y este pack como **`cj-skate`**, **uno al lado del otro** (en la misma carpeta). Así `skate.bat` /
`skate.sh` encuentran el instalador del mod.

## Preparar (Windows)

1. Abre `cj-skate\motor\skate3rust.exe` **una vez**: su asistente te pide tu `default.xex` (o la ISO) y convierte tus archivos. La primera vez tarda.
   Cuando termine, ciérralo.
2. En una terminal abierta en la carpeta que contiene `cj-mod` y `cj-skate`:
   ```
   python cj-mod\setup\cj_setup.py skate --engine cj-skate
   ```
   Termina con **"skate listo"**.

## Preparar (Linux)

```
pip install numpy pillow
python3 cj-mod/setup/cj_setup.py skate --engine cj-skate --skate3 "/ruta/a/Skate 3"
```

La carpeta de `--skate3` es la que tiene `default.xex`. La primera conversión tarda (varios minutos y algunos GB). El paso del audio se salta:
el motor corre sin sonido. Termina con **"skate listo"**.

## Jugar

1. **GTA** (con `jugar.bat` / `./jugar.sh` del mod) y carga tu partida.
2. **El motor de Skate**: `cj-skate\skate.bat` / `./cj-skate/skate.sh`. Se abre su ventana: déjala abierta.
3. **Minecraft** con Fabric. Con el **skateboard** en la mano (está en la pestaña de herramientas y tiene receta), **clic derecho** o **L3 + R3** en el
   mando para subirte y bajarte. **Triángulo** te baja y te deja caminar con la tabla, y otra vez te sube. La cámara se pone detrás sola.

## Problemas comunes

- **"Skate: the Skate engine is not running"**: abre `skate.bat` / `skate.sh` antes de subirte a la tabla.
- **"no hay archivos de Skate 3 convertidos"**: en Windows, abre `motor\skate3rust.exe` y completa su asistente; en Linux, usa `--skate3`.
- **"faltan numpy y Pillow"**: `pip install numpy pillow`.
- **"no encuentro cj_setup.py"**: pon `cj-skate` al lado de `cj-mod`, o indica la ruta con la variable `CJ_SETUP`.
- **El firewall pregunta por skate3rust**: permite la red local; el motor y Minecraft hablan en tu propio equipo (127.0.0.1).
