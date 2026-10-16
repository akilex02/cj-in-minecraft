#!/usr/bin/env python3
"""Converts CJ from the user's own GTA San Andreas files into cj.glb (skinned mesh, skeleton and textures)."""
import argparse
import os
import sys
import tempfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from cjconvert.assemble import ConversionError, load_model, load_weapon  # noqa: E402
from cjconvert.bodyblend import body_ratios  # noqa: E402
from cjconvert.clothes import load_outfit, resolve_stats  # noqa: E402
from cjconvert.vehicles import PAINT, VEHICLES, load_vehicle  # noqa: E402
from cjconvert.weapons import WEAPON_MODELS  # noqa: E402
from cjconvert.glb import to_glb  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent  # the project root, whatever the working directory
DEFAULT_OUT = ROOT / "cj-fabric" / "run" / "config" / "cj_fabric"  # git-ignored
DEFAULT_GTA = ROOT / "GTA San Andreas"


def report(model, size):
    lines = [f"CJ assembled from {len(model.parts)} parts on a skeleton of {len(model.skeleton.node_ids)} bones:"]
    for part in model.parts:
        lines.append(f"  {part.name:6} {len(part.positions):4} vertices {len(part.triangles):4} triangles  texture {part.texture}")
    zs = [p[2] for part in model.parts for p in part.positions]
    lines.append(f"  height {max(zs) - min(zs):.2f} m; {len(model.textures)} textures; cj.glb is {size} bytes")
    return "\n".join(lines)


def write_atomically(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, tmp = tempfile.mkstemp(dir=path.parent, prefix=".cj-", suffix=".tmp")
    try:
        with os.fdopen(fd, "wb") as f:
            f.write(data)
        os.replace(tmp, path)
    except BaseException:
        if os.path.exists(tmp):
            os.unlink(tmp)
        raise


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--gta", default=str(DEFAULT_GTA), help="the GTA San Andreas folder (the one with models/)")
    ap.add_argument("--out", default=str(DEFAULT_OUT), help="where to write cj.glb")
    ap.add_argument("--outfit", help="the cj_outfit.json the bridge writes in the GTA folder: CJ is dressed and built like in that save (his clothes, muscle and fat)")
    ap.add_argument("--muscle", type=float, help="the muscle stat (0 to 1000) of the body, instead of the one of --outfit")
    ap.add_argument("--fat", type=float, help="the fat stat (0 to 1000) of the body, instead of the one of --outfit")
    ap.add_argument("--preview", action="store_true", help="also write cj_preview.png (needs numpy and Pillow)")
    ap.add_argument("--weapons", action="store_true", help="also convert the weapons (weapons/<name>.glb)")
    ap.add_argument("--weapons-only", action="store_true", help="convert only the weapons, not CJ")
    ap.add_argument("--vehicles", action="store_true", help="also convert the vehicles (vehicles/<name>.glb)")
    ap.add_argument("--vehicles-only", action="store_true", help="convert only the vehicles, not CJ or the weapons")
    args = ap.parse_args(argv)
    out = Path(args.out)
    try:
        convert_cj = not (args.weapons_only or args.vehicles_only)
        if convert_cj:
            stats = resolve_stats(args.outfit, args.muscle, args.fat)
            dressing = load_outfit(args.outfit) if args.outfit else None
            model = load_model(Path(args.gta), stats=stats, dressing=dressing)
            if stats is not None:
                ripped, fatness, normal = body_ratios(*stats)
                print(f"body of muscle {stats[0]:g} and fat {stats[1]:g}: {ripped:.0%} ripped, {fatness:.0%} fat, {normal:.0%} normal")
            data = to_glb(model)
        weapon_files = {}
        if (args.weapons or args.weapons_only) and not args.vehicles_only:
            for name in WEAPON_MODELS:
                weapon_files[name] = to_glb(load_weapon(Path(args.gta), name))
        vehicle_files = {}
        if args.vehicles or args.vehicles_only:
            for file_name, model_name in VEHICLES:
                vehicle_files[file_name] = to_glb(load_vehicle(Path(args.gta), model_name, PAINT.get(model_name)))
    except (ConversionError, ValueError) as e:
        print(f"error: {e}")
        return 1
    if convert_cj:
        write_atomically(out / "cj.glb", data)
        print(report(model, len(data)))
        print(f"wrote {out / 'cj.glb'}")
    for name, blob in weapon_files.items():
        write_atomically(out / "weapons" / f"{name}.glb", blob)
        print(f"wrote {out / 'weapons' / (name + '.glb')} ({len(blob)} bytes)")
    for name, blob in vehicle_files.items():
        write_atomically(out / "vehicles" / f"{name}.glb", blob)
        print(f"wrote {out / 'vehicles' / (name + '.glb')} ({len(blob)} bytes)")
    if args.preview and convert_cj:
        try:
            from preview_glb import render_preview
        except ImportError:
            print("error: --preview needs numpy and Pillow (pip install numpy pillow)")
            return 1
        image = render_preview(data)
        preview = out / "cj_preview.png"
        image.save(preview)
        print(f"wrote {preview}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
