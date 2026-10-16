# Fuente del motor de Skate de este pack

Este pack trae el **motor de Skate 3 en Rust** (https://github.com/SK8-ENGINE/skate-3-rust-engine), licencia **GPL-3.0** (ver
`motor/licenses/`), compilado con un único cambio: el parche `skate-engine-host.patch`, que añade el **modo anfitrión** (`--host`): el mod de
Minecraft controla al patinador por UDP en tu propio equipo (127.0.0.1) y recibe su pose.

- Repositorio original: https://github.com/SK8-ENGINE/skate-3-rust-engine
- Commit: `2e16697797c8358d16f747a8259354ff2921041a`
- Cambio: `skate-engine-host.patch` (en esta carpeta)

## Reconstruir el ejecutable

```
git clone https://github.com/SK8-ENGINE/skate-3-rust-engine
cd skate-3-rust-engine
git checkout 2e16697797c8358d16f747a8259354ff2921041a
git apply ../skate-engine-host.patch
```

- **Linux**: `cargo build --release -p skate-game --bin skate3rust --no-default-features` (queda en `target/release/skate3rust`).
- **Windows**: `scripts\Build-Release.ps1` (Rust con MSVC, LLVM y Python 3.13; deja el paquete portátil en `target\skate3rust-windows-x64.zip`).

**No incluye nada de Skate 3.** El juego y sus archivos convertidos son tuyos y se quedan en tu equipo.
