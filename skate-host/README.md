# Parche "modo anfitrión" para el motor de Skate 3

Extra opcional del mod: permite que otro programa (el mod de Minecraft) controle al patinador del [motor de Skate 3 en Rust](https://github.com/SK8-ENGINE/skate-3-rust-engine) por UDP. El motor es GPL-3.0; este parche se publica con su fuente. **No incluye nada de Skate 3**: cada usuario aporta su propia copia del juego (`default.xex` y su carpeta `data`).

## Qué agrega
`--host ADDR` (por ejemplo `--host 127.0.0.1:34600`): el motor abre un socket UDP; cada datagrama de entrada reemplaza a lo que daría un mando y, tras cada tick de física (60 por segundo), responde con la pose. Protocolo (little-endian):
- **Entrada** (88 bytes): magia `0x48384B53` (u32), versión 1 (u16), tipo 1 (u16), secuencia (u32), banderas (u32, sin uso), 18 acciones (f32, de -1 a 1, en el orden de `skate-core/src/input/gameplay_map.rs`: 0 palanca izq. X, 1 palanca izq. Y, 3-4 palanca der., 6-7 gatillos, 14 X, 15 Y, 16 A, 17 B...).
- **Pose** (16 + 64 + 48 + 64 por hueso bytes): cabecera igual con tipo 2, tick (u32), número de huesos (u32), matriz de la raíz (16 f32), base y posición del tablero (9 + 3 f32), y las matrices globales de los huesos (16 f32 cada una; 36 con la raíz). Coordenadas nativas del motor (Y arriba, metros).

- **Mundo** (tipo 3, región): id de región (u32), número de cuadriláteros (u32, máximo 1000) y cada uno como cuatro esquinas de tres f32 (48 bytes). Reemplaza el contenido de esa región; el motor junta todas las regiones y reconstruye la colisión (1714 cuadriláteros: 0.5 ms). Un número roto o fuera de ±100000 rechaza el datagrama entero. **Orden de las esquinas:** los triángulos son (0, 2, 1) y (0, 3, 2), de **una sola cara**, y la cara sólida es la que mira hacia afuera con normal (esquina 2 − esquina 0) × (esquina 1 − esquina 0): un suelo es (x0, z0), (x1, z0), (x1, z1), (x0, z1) con x1 > x0 y z1 > z0. Una cara vista por detrás no existe.
- **Borrar región** (tipo 4): id de región (u32).
- **Teletransporte** (tipo 5): x, y, z, rumbo (radianes sobre Y; 0 mira a +Z) y velocidad x, y, z (7 f32). Coloca al patinador sobre el tablero.

## Estado
- Prueba 1 (Linux): con `tools/skate_host_probe.py` el patinador empuja y avanza (53 m en 8 s).
- Prueba 2 (colisión desde bloques): con `tools/skate_host_probe.py --blocks` se manda un suelo de 41 × 41 bloques y una pared; el patinador rueda sobre el suelo a una altura constante (2.09 con el suelo a 2.0), **se detiene en la pared** y cae por el borde.
- Falta el cliente de Minecraft (convertir los bloques en cuadriláteros, mover al jugador, dibujar a CJ con los huesos reasignados) y el empaquetado (ver `docs/skate-spike-results.md`).

## Base
Commit `2e16697797c8358d16f747a8259354ff2921041a` de `SK8-ENGINE/skate-3-rust-engine`. Aplicar con `git apply skate-engine-host.patch`.

## Construir y probar en Linux
```bash
git clone https://github.com/SK8-ENGINE/skate-3-rust-engine src && cd src
git checkout 2e16697797c8358d16f747a8259354ff2921041a && git apply ../skate-engine-host.patch
cargo build --release -p skate-game --bin skate3rust
# contenido de tus archivos (el instalador es Python; en Linux el paso de audio descarga un .exe de Windows y falla: omítelo, no hace falta)
export LD_LIBRARY_PATH=$PWD/target/release/deps:$(rustc --print sysroot)/lib
./target/release/skate3rust --assets <carpeta convertida>/assets --test-world --host 127.0.0.1:34600 --mute
python3 ../tools/skate_host_probe.py --blocks 14   # en otra terminal (sin --blocks usa el mundo de prueba del motor)
```
Nota: en la máquina de la prueba, `rustc` se cayó varias veces con segmentación dentro de LLVM al compilar `skate-game` (artefactos corruptos tras fallos); `cargo clean -p skate-game -p bevy_pbr -p bevy_sprite_render` y repetir con `-j 8` lo resolvió.
