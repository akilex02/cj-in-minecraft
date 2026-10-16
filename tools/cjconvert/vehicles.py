"""The vehicles: models of gta3.img (a .dff and a .txd of the same name) with the textures every car shares in models/generic/vehicle.txd."""
from pathlib import Path

from .assemble import ConversionError, MeshPart, Model
from .dff import Skeleton, parse_vehicle_geometry
from .img import ImgArchive
from .rwchunk import FormatError
from .txd import Texture, parse_txd

# the name of the file we write -> the name of the model in the game
VEHICLES = (("greenwood", "greenwoo"),)

# the paint we give a car instead of the one of carcols.dat: the light grey-blue Greenwood with a cream-white roof (matched by eye to the user's picture)
PAINT = {"greenwoo": ((118, 145, 150), (230, 226, 205))}

SPACE = "GTA vehicle space: X right, Y forward, Z up"
WHEELS = {"lf": "wheel_lf_dummy", "rf": "wheel_rf_dummy", "lb": "wheel_lb_dummy", "rb": "wheel_rb_dummy"}
SEAT_FRAME = "ped_frontseat"
WHEEL_FRAME = "wheel"
BODY_FRAME = "chassis"
IDENTITY = (1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0)

# The colours GTA gives the materials that take the paint of the car, and those of its lights (which it switches on and off: the file shows them off).
PRIMARY, SECONDARY, THIRD, FOURTH = (60, 255, 0), (255, 0, 175), (0, 255, 255), (255, 255, 0)
LIGHTS = {(185, 255, 0): (150, 150, 140), (255, 175, 0): (150, 100, 0), (255, 60, 0): (100, 10, 10), (0, 255, 200): (150, 175, 170)}
# The lenses of the lights are told apart by where they are, not by their colour (the colours of the file change from car to car): those of the texture of the
# lights that are at the front of the car (y above LAMP_Y) are the headlights and those at the back (y below -LAMP_Y) the tail lights. The indicators (amber and
# the like) and the mirrors, which use the same texture, are left alone. Each lens has an off texture and a lit twin that the file says is its swap.
LAMP_TEXTURE = "vehiclelights128"
LAMP_Y = 1.5
LAMP_TINTS = {(150, 150, 140), (100, 10, 10)}  # the dim colours (see LIGHTS) of the lenses of the file; a lens with no tint at all is a lamp too
FRONT_OFF, REAR_OFF = (170, 170, 160), (100, 10, 10)
FRONT_GLOW, REAR_GLOW = (255, 246, 205), (255, 30, 30)


def paint_colour(color, primary, secondary):
    """The colour a material is drawn with: the paint of the car where the file has GTA's paint colours, a dim version of a light, any other as it is."""
    r, g, b, a = color
    rgb = (r, g, b)
    if rgb in (PRIMARY, THIRD):
        return (*primary, a)
    if rgb in (SECONDARY, FOURTH):
        return (*secondary, a)
    if rgb in LIGHTS:
        return (*LIGHTS[rgb], a)
    return color


def default_paint(gta_dir, name):
    """The two colours (rgb) the car has by default, from data/carcols.dat: the first set of its entry in the four-colour table, or else the two-colour one."""
    path = Path(gta_dir) / "data" / "carcols.dat"
    if not path.is_file():
        raise ConversionError(f"{path} was not found")
    palette, sets, section = [], {}, None
    for line in path.read_text(errors="replace").splitlines():
        line = line.split("#")[0].strip()
        if not line:
            continue
        if line.lower() in ("col", "car", "car4", "end") and "," not in line:
            section = line.lower()  # "end" is a section nobody reads
            continue
        if section == "col":
            try:
                color = tuple(int(v) for v in line.split(","))
            except ValueError:
                color = None  # the game's own file has a mistyped line (a dot for a comma); only a car that uses it fails
            palette.append(color if color is not None and len(color) == 3 else None)
        elif section in ("car", "car4"):
            fields = [f.strip() for f in line.split(",")]
            try:
                numbers = [int(v) for v in fields[1:] if v != ""]
            except ValueError:
                numbers = None  # likewise: a mistyped line breaks only the car it belongs to
            sets.setdefault(fields[0].lower(), {})[section] = numbers
    entry = sets.get(name.lower())
    if not entry:
        raise ConversionError(f"{path.name} has no colours for {name}")
    numbers = entry["car4"] if "car4" in entry else entry["car"]
    if numbers is None:
        raise ConversionError(f"{path.name} has a line for {name} that is not understood")
    numbers = numbers[:2]
    if len(numbers) < 2:
        raise ConversionError(f"{path.name} gives {name} fewer than two colours")
    for n in numbers:
        if not 0 <= n < len(palette) or palette[n] is None:
            raise ConversionError(f"{path.name} gives {name} the colour {n}, which the palette ({len(palette)} colours) does not have")
    return palette[numbers[0]], palette[numbers[1]]


def _read(archive, filename, model):
    try:
        return archive.read(filename)
    except KeyError as e:
        raise ConversionError(f"{model}: {e.args[0]}") from e
    except FormatError as e:
        raise ConversionError(f"{model}: {e}") from e


def tint(texture, colour, key):
    """The texture with every pixel multiplied by the colour (what GTA does with the colour of a material)."""
    rgba = bytearray(texture.rgba)
    for channel in range(4):
        factor = colour[channel]
        rgba[channel::4] = bytes((v * factor + 127) // 255 for v in texture.rgba[channel::4])
    return Texture(key, texture.width, texture.height, bytes(rgba))


def _round(vector):
    return [round(v, 4) for v in vector]


def glow(texture, colour, key):
    """The texture shone on by a lamp of this colour: almost all the colour, with a little of the texture left."""
    rgba = bytearray(texture.rgba)
    for channel in range(3):
        rgba[channel::4] = bytes(min(255, int(v * 0.2 + colour[channel] * 0.8 + 0.5)) for v in texture.rgba[channel::4])
    return Texture(key, texture.width, texture.height, bytes(rgba))


def _is_lamp_key(key):
    """A key of the texture of the lights that is untinted or tinted with the dim colour of a lens of the file."""
    if LAMP_TEXTURE not in key:
        return False
    if "@" not in key:
        return True
    tint = key.rsplit("@", 1)[1].split(",")
    return len(tint) == 4 and tuple(int(v) for v in tint[:3]) in LAMP_TINTS


def load_vehicle(gta_dir, name, paint=None):
    """A vehicle as one model on a single joint: the body in as many meshes as it has materials, then the wheel (once, centred on its own frame).
    The places of the four wheels, the driver's seat and the radius of the wheel travel in the model's extras."""
    gta_dir = Path(gta_dir)
    img_path = gta_dir / "models" / "gta3.img"
    if not img_path.is_file():
        raise ConversionError(f"{img_path} was not found; point --gta at your GTA San Andreas folder")
    shared_path = gta_dir / "models" / "generic" / "vehicle.txd"
    if not shared_path.is_file():
        raise ConversionError(f"{shared_path} was not found: the textures every vehicle shares are in vehicle.txd")
    primary, secondary = paint if paint else default_paint(gta_dir, name)
    try:
        archive = ImgArchive(img_path)
    except FormatError as e:
        raise ConversionError(f"{img_path.name}: {e}") from e
    dff_data = _read(archive, f"{name}.dff", name)
    txd_data = _read(archive, f"{name}.txd", name)
    try:
        frames, atomics = parse_vehicle_geometry(dff_data)
        own = parse_txd(txd_data)
        shared = parse_txd(shared_path.read_bytes())
    except FormatError as e:
        raise ConversionError(f"{name}: {e}") from e
    by_name = {f.name: i for i, f in enumerate(frames) if f.name}
    for needed in (*WHEELS.values(), SEAT_FRAME, WHEEL_FRAME):
        if needed not in by_name:
            raise ConversionError(f"{name}.dff has no frame named {needed!r}")

    textures = {}
    sources = {}  # the key of a tinted texture -> the texture it is made from, for the lit twins of the lights

    def key_of(material):
        """The key of the texture a material is drawn with, adding the texture to the model the first time."""
        if material.texture is not None:
            # the colour of a textured material multiplies its texture: that is how the grey body texture takes the paint of the car
            colour = paint_colour(material.color, primary, secondary)
            for key, dictionary in ((f"{name}:{material.texture}", own), (f"vehicle:{material.texture}", shared)):
                if material.texture in dictionary:
                    if colour == (255, 255, 255, 255):
                        textures.setdefault(key, dictionary[material.texture])
                        sources.setdefault(key, dictionary[material.texture])
                        return key
                    key = f"{key}@{colour[0]},{colour[1]},{colour[2]},{colour[3]}"
                    if key not in textures:
                        textures[key] = tint(dictionary[material.texture], colour, key)
                        sources[key] = dictionary[material.texture]
                    return key
            raise ConversionError(f"neither {name}.txd nor vehicle.txd has a texture named {material.texture!r}")
        r, g, b, a = paint_colour(material.color, primary, secondary)
        key = f"colour:{r},{g},{b},{a}"
        textures.setdefault(key, Texture(key, 1, 1, bytes((r, g, b, a))))
        return key

    positions, normals, uvs, grouped = [], [], [], {}
    wheel = None
    for frame_index, pos, nor, uv, triangles, per_triangle, materials in atomics:
        if frame_index >= len(frames):
            raise ConversionError(f"{name}.dff has an atomic on the frame {frame_index} but only {len(frames)} frames")
        frame = frames[frame_index]
        if frame.name == WHEEL_FRAME and wheel is None:
            local = (frame.local[0:3], frame.local[3:6], frame.local[6:9], frame.local[9:12])
            wheel = ([_apply_local(local, v) for v in pos], [_apply_local(local, v, point=False) for v in nor] or [(0.0, 0.0, 1.0)] * len(pos),
                     list(uv) or [(0.0, 0.0)] * len(pos), triangles, per_triangle, materials)
            continue
        if not (frame.name == BODY_FRAME or (frame.name.endswith("_ok") and not frame.name.startswith("ug_"))):
            continue  # damaged pieces, the low-resolution chassis, the underside and the dummies are not drawn
        world = (frame.right, frame.up, frame.at, frame.position)
        base = len(positions)
        positions += [_apply_local(world, v) for v in pos]
        normals += [_apply_local(world, v, point=False) for v in nor] if nor else [(0.0, 0.0, 1.0)] * len(pos)
        uvs += list(uv) if uv else [(0.0, 0.0)] * len(pos)
        for triangle, m in zip(triangles, per_triangle):
            grouped.setdefault(key_of(materials[m]), []).append(tuple(v + base for v in triangle))
    if not any(frames[frame_index].name == BODY_FRAME for frame_index, *_rest in atomics):
        raise ConversionError(f"{name}.dff has no {BODY_FRAME!r} geometry")
    if wheel is None:
        raise ConversionError(f"{name}.dff has no {WHEEL_FRAME!r} geometry")

    swaps = []
    lamp_groups = {}
    for key, triangles in list(grouped.items()):
        if not _is_lamp_key(key):
            continue
        source = sources.get(key)
        if source is None:
            continue
        keep = []
        for triangle in triangles:
            y = sum(positions[v][1] for v in triangle) / 3
            where = "front" if y > LAMP_Y else "rear" if y < -LAMP_Y else None
            if where is None:
                keep.append(triangle)
            else:
                lamp_groups.setdefault((where, key), []).append(triangle)
        if keep:
            grouped[key] = keep
        else:
            del grouped[key]
    for (where, key), triangles in lamp_groups.items():
        off_key, lit_key = f"{key}@{where}", f"{key}@{where}@lit"
        off, glowing = (FRONT_OFF, FRONT_GLOW) if where == "front" else (REAR_OFF, REAR_GLOW)
        if off_key not in textures:
            textures[off_key] = tint(sources[key], (*off, 255), off_key)
            textures[lit_key] = glow(sources[key], glowing, lit_key)
            keys = list(textures)
            swaps.append([keys.index(off_key), keys.index(lit_key)])
        grouped.setdefault(off_key, []).extend(triangles)

    parts = []
    for i, (key, triangles) in enumerate(grouped.items()):
        parts.append(_mesh(f"body_{i}", positions, normals, uvs, triangles, key))
    w_pos, w_nor, w_uv, w_triangles, w_per_triangle, w_materials = wheel
    wheel_groups = {}
    for triangle, m in zip(w_triangles, w_per_triangle):
        wheel_groups.setdefault(key_of(w_materials[m]), []).append(triangle)
    for i, (key, triangles) in enumerate(wheel_groups.items()):
        parts.append(_mesh("wheel" if len(wheel_groups) == 1 else f"wheel_{i}", w_pos, w_nor, w_uv, triangles, key))
    ys = [v[1] for v in w_pos]
    zs = [v[2] for v in w_pos]
    seat = frames[by_name[SEAT_FRAME]].position
    extras = {
        "space": SPACE,
        "vehicle": name,
        "wheelRadius": round(max(max(ys) - min(ys), max(zs) - min(zs)) / 2, 4),
        "wheels": {corner: _round(frames[by_name[frame]].position) for corner, frame in WHEELS.items()},
        "driverSeat": _round((-seat[0], seat[1], seat[2])),  # the file has the front passenger's seat; GTA mirrors it for the driver
        "paint": [list(primary), list(secondary)],
        "lightSwaps": swaps,  # [dim texture, lit texture] pairs of the images: with the lights on the first is drawn as the second
    }
    return Model(Skeleton((0,), (IDENTITY,)), parts, textures, extras)


def _apply_local(matrix, v, point=True):
    right, up, at, pos = matrix
    return tuple(v[0] * right[k] + v[1] * up[k] + v[2] * at[k] + (pos[k] if point else 0.0) for k in range(3))


def _mesh(name, positions, normals, uvs, triangles, key):
    """A mesh with only the vertices its triangles use."""
    used = sorted({v for tri in triangles for v in tri})
    new = {old: i for i, old in enumerate(used)}
    return MeshPart(name, [positions[v] for v in used], [normals[v] for v in used], [uvs[v] for v in used],
                    [(new[a], new[b], new[c]) for a, b, c in triangles], [(0, 0, 0, 0)] * len(used), [(1.0, 0.0, 0.0, 0.0)] * len(used), key)
