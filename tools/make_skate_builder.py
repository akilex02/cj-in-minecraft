#!/usr/bin/env python3
"""Zips what building the Windows skate pack on Windows needs (make_skate_pack.ps1, its audit, the pack's files and the host patch), in the folders the
script expects, with a LEEME.txt of the steps. Usage: make_skate_builder.py <version>  ->  build/release/cj-skate-builder-<version>.zip"""
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
FILES = ("tools/make_skate_pack.ps1", "tools/skate_pack_audit.py", "tools/skate_pack/skate.bat", "tools/skate_pack/LEEME-skate.md",
         "tools/skate_pack/FUENTE.md", "skate-host/skate-engine-host.patch")
README = """Generar el pack de skate para Windows
=====================================

1. Descomprime esta carpeta en tu Windows.
2. Instala una sola vez:
   - git (https://git-scm.com/download/win)
   - Rust con la cadena MSVC (https://rustup.rs/) y las "Build Tools" de Visual Studio que pide
   - LLVM en su carpeta por defecto (https://github.com/llvm/llvm-project/releases)
   - CMake (https://cmake.org/download/)
   - Python 3.13 (https://www.python.org/downloads/), con "Add python.exe to PATH"
3. Abre PowerShell en esta carpeta y ejecuta:

   powershell -ExecutionPolicy Bypass -File tools\\make_skate_pack.ps1 -Version {version}

   Baja el motor de Skate 3 en Rust a C:\\cjsk (una carpeta corta: Windows no admite rutas de mas de 260 caracteres y la
   compilacion crea rutas muy largas), le aplica el parche del modo anfitrion y lo compila con su propio empaquetado
   (tarda bastante la primera vez). Para usar otra carpeta corta:  ... -Version {version} -WorkDir D:\\sk
   Al terminar dice:  pack: ...\\build\\release\\cj-skate-{version}-windows.zip

4. Ese zip es el pack de skate de Windows: descomprimelo junto a la carpeta del mod (cj-mod-...) y sigue su LEEME-skate.md.

Si algo falla, copia el mensaje de error completo.
"""


def main(argv):
    if len(argv) != 2:
        print(__doc__)
        return 2
    version = argv[1]
    name = f"cj-skate-builder-{version}"
    out = ROOT / "build" / "release" / f"{name}.zip"
    out.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        for f in FILES:
            z.write(ROOT / f, f"{name}/{f}")
        z.writestr(f"{name}/LEEME.txt", README.format(version=version).replace("\n", "\r\n"))
    print(f"builder: {out}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
