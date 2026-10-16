"""Assembles CJ from the skeleton of player.dff and the body parts of player.img."""
from dataclasses import dataclass, field
from pathlib import Path

from .bodyblend import blend_parts, body_ratios
from .dressing import BARE_TEXTURES, DEFAULT_MODELS, OWN_TEXTURE_SLOT, SLOT_PARTS, SLOT_TEXTURE, TORSO_LAYERS, blend_skin, place
from . import clothes
from .errors import ConversionError
from .dff import Skeleton, clump_count, parse_part, parse_skeleton, parse_static_part
from .img import ImgArchive
from .rwchunk import FormatError
from .txd import Texture, parse_txd
from .weapons import ASSEMBLED, ATTACH_NODES, RIGHT_HAND_NODE

WEIGHT_TOLERANCE = 0.01


@dataclass(frozen=True)
class PartSpec:
    name: str
    dff: str
    txd: str
    texture: str
    overlay: tuple = None  # (txd, texture) painted over the texture by its alpha: the clothes of the torso lie over CJ's own skin


# CJ as at the start of the game, thin, in his everyday clothes: his own face and skin, a white vest over the torso (its texture is painted over the skin
# one, which the hands share), blue jeans and black chucks (they replace the legs and the bare feet).
OUTFIT = (
    PartSpec("torso", "torso.dff", "player_torso.txd", "torso", ("vest.txd", "vest")),
    PartSpec("head", "head.dff", "player_face.txd", "face"),
    PartSpec("hands", "hands.dff", "player_torso.txd", "torso", ("vest.txd", "vest")),
    PartSpec("legs", "jeans.dff", "jeansdenim.txd", "legs"),
    PartSpec("feet", "chucks.dff", "chucksblack.txd", "chucksblack"),
)


@dataclass(frozen=True)
class MeshPart:
    name: str
    positions: list
    normals: list
    uvs: list
    triangles: list
    joints: list    # 4 skeleton joint indices per vertex
    weights: list   # 4 weights per vertex, summing to 1
    texture: str    # key into Model.textures


@dataclass(frozen=True)
class Model:
    skeleton: Skeleton
    parts: list
    textures: dict
    extras: dict = field(default_factory=dict)  # more for the file's asset.extras (the vehicles' wheel places...)


def remap_part(name, part, skeleton):
    """Part bone index -> nodeID (through the part's own hierarchy) -> joint index of the same nodeID in the skeleton."""
    joint_of = {node_id: i for i, node_id in enumerate(skeleton.node_ids)}
    joints, weights = [], []
    for v, (bones, ws) in enumerate(zip(part.bone_indices, part.weights)):
        row_joints, row_weights = [], []
        for bone, w in zip(bones, ws):
            if w <= 0.0:
                row_joints.append(0)
                row_weights.append(0.0)
                continue
            if bone >= len(part.hierarchy_ids):
                raise ConversionError(f"{name}: vertex {v} uses bone {bone} but the part only has {len(part.hierarchy_ids)}")
            node_id = part.hierarchy_ids[bone]
            if node_id not in joint_of:
                raise ConversionError(f"{name}: vertex {v} is weighted to bone {node_id}, which the skeleton does not have")
            row_joints.append(joint_of[node_id])
            row_weights.append(w)
        total = sum(row_weights)
        if abs(total - 1.0) > WEIGHT_TOLERANCE:
            raise ConversionError(f"{name}: the weights of vertex {v} add up to {total:.3f}, not 1")
        joints.append(tuple(row_joints))
        weights.append(tuple(w / total for w in row_weights))
    return joints, weights


def composite(base, over):
    """The texture `over` (with its alpha) painted on `base`, which keeps its own alpha. Both must have the same size."""
    if (base.width, base.height) != (over.width, over.height):
        raise ConversionError(f"cannot paint {over.name} ({over.width}x{over.height}) over {base.name} ({base.width}x{base.height})")
    out = bytearray(base.rgba)
    for i in range(0, len(out), 4):
        a = over.rgba[i + 3]
        if a == 0:
            continue
        for c in range(3):
            out[i + c] = (over.rgba[i + c] * a + out[i + c] * (255 - a) + 127) // 255
    return Texture(f"{base.name}+{over.name}", base.width, base.height, bytes(out))


# The body files hold three clumps, one per build of CJ. The third is the thin one (the narrowest legs and the flattest belly); the first is the muscular one
# the model had at the beginning, and the second the fat one.
BODY_THIN = 2


def load_model(gta_dir, outfit=OUTFIT, body=BODY_THIN, stats=None, dressing=None):
    """`stats` = (muscle, fat) of a save: the body is the blend of the three builds that the game makes of them. Without it, the build `body` (the normal one).
    `dressing` = the clothes.Outfit of a save: CJ wears what the game says he wears (the models of its slots, the textures over his skin) instead of `outfit`."""
    models = Path(gta_dir) / "models"
    skeleton_img = models / "gta3.img"
    parts_img = models / "player.img"
    for path in (skeleton_img, parts_img):
        if not path.is_file():
            raise ConversionError(f"{path} was not found; point --gta at your GTA San Andreas folder")
    try:
        gta3 = ImgArchive(skeleton_img)
        player = ImgArchive(parts_img)
    except FormatError as e:
        raise ConversionError(str(e)) from e

    def read(img, name):
        try:
            return img.read(name)
        except KeyError as e:
            raise ConversionError(e.args[0]) from e
        except FormatError as e:
            raise ConversionError(str(e)) from e

    def parse(label, fn, data):
        try:
            return fn(data)
        except FormatError as e:
            raise ConversionError(f"{label}: {e}") from e

    ratios = body_ratios(*stats) if stats is not None else None

    def load_part(dff):
        part_data = read(player, dff)
        builds = clump_count(part_data)
        if ratios is not None and builds >= 3:  # ripped, fat, normal: blended by the stats, as the game does
            try:
                return blend_parts([parse(dff, lambda d, k=k: parse_part(d, k), part_data) for k in range(3)], ratios)
            except ValueError as e:
                raise ConversionError(f"{dff}: {e}") from e
        build = min(body, max(builds, 1) - 1)  # a file with fewer builds gives its last
        return parse(dff, lambda d: parse_part(d, build), part_data)

    def txd(name):
        return parse(name, parse_txd, read(player, name))

    def torso_skin():
        skins = txd("player_torso.txd")
        missing = [n for n in ("torso_ripped", "torso_fat", "torso") if n not in skins]
        if missing:
            raise ConversionError(f"player_torso.txd has no texture named {missing[0]!r}; it has: {', '.join(sorted(skins))}")
        return blend_skin((skins["torso_ripped"], skins["torso_fat"], skins["torso"]), ratios or (0.0, 0.0, 1.0))

    skeleton = parse("player.dff", parse_skeleton, read(gta3, "player.dff"))
    textures, entries = {}, []  # entries: (part name, dff file, key of its texture)
    if dressing is None:
        for spec in outfit:
            dictionary = txd(spec.txd)
            if spec.texture not in dictionary:
                raise ConversionError(f"{spec.txd} has no texture named {spec.texture!r}; it has: {', '.join(sorted(dictionary))}")
            key = f"{spec.txd.rsplit('.', 1)[0]}:{spec.texture}"
            texture = dictionary[spec.texture]
            if ratios is not None and spec.txd == "player_torso.txd" and spec.texture == "torso" and {"torso_ripped", "torso_fat"} <= set(dictionary):
                texture = torso_skin()  # the muscle shows in the skin as well: the three skins of the torso, blended
            if spec.overlay:
                over_txd, over_name = spec.overlay
                over_dictionary = txd(over_txd)
                if over_name not in over_dictionary:
                    raise ConversionError(f"{over_txd} has no texture named {over_name!r}; it has: {', '.join(sorted(over_dictionary))}")
                texture = composite(texture, over_dictionary[over_name])
                key = f"{key}+{over_txd.rsplit('.', 1)[0]}:{over_name}"
            textures[key] = texture
            entries.append((spec.name, spec.dff, key))
    else:
        entries = _dress(player, txd, torso_skin, dressing, textures)
    parts = []
    for name, dff, key in entries:
        part = load_part(dff)
        joints, weights = remap_part(name, part, skeleton)
        parts.append(MeshPart(name, part.positions, part.normals, part.uvs, part.triangles, joints, weights, key))
    return Model(skeleton, parts, textures)


def _dress(player, txd, torso_skin, outfit, textures):
    """The parts and textures of what a save says CJ wears. Fills `textures` and returns the (part name, dff file, texture key) of each part."""
    models, txds = {}, {}
    for file in player.names():
        stem, _, extension = file.rpartition(".")
        if extension.lower() == "dff":
            models[clothes.uppercase_key(stem)] = stem
        elif extension.lower() == "txd":
            txds[clothes.uppercase_key(stem)] = stem

    def first_texture(slot_name, key):
        if key not in txds:
            raise ConversionError(f"the {slot_name} texture of the outfit (key {key}) names no .txd of player.img")
        dictionary = txd(txds[key] + ".txd")
        if not dictionary:
            raise ConversionError(f"{txds[key]}.txd has no textures")
        return next(iter(dictionary.values())), txds[key]

    built = {}  # texture target -> (key, Texture)

    def target_texture(target):
        if target in built:
            return built[target]
        if target == "torso":
            texture = torso_skin()
            names = ["torso"]
            for slot in TORSO_LAYERS:
                if outfit.textures[slot]:
                    layer, stem = first_texture("torso" if slot == 0 else clothes.TEXTURE_SLOTS[slot], outfit.textures[slot])
                    texture = place(texture, layer)
                    names.append(stem)
            result = ("dressed:" + "+".join(names), texture)
        else:
            slot = OWN_TEXTURE_SLOT[target]
            if outfit.textures[slot] and not (target == "head" and txds.get(outfit.textures[slot]) == "head"):
                texture, stem = first_texture(clothes.TEXTURE_SLOTS[slot], outfit.textures[slot])
                result = (f"dressed:{target}:{stem}", texture)
            elif target in BARE_TEXTURES:
                file, name = BARE_TEXTURES[target]
                dictionary = txd(file)
                texture = dictionary[name] if name else next(iter(dictionary.values()))
                result = (f"dressed:{target}:{file.rsplit('.', 1)[0]}", texture)
            else:
                raise ConversionError(f"the {target} model of the outfit has no texture: its texture slot is empty")
        built[target] = result
        textures[result[0]] = result[1]
        return result

    entries = []
    for slot, part_name in enumerate(SLOT_PARTS):
        key = outfit.models[slot]
        if key:
            if key not in models:
                raise ConversionError(f"the {part_name} model of the outfit (key {key}) names no .dff of player.img")
            dff = models[key] + ".dff"
        elif slot < len(DEFAULT_MODELS):
            dff = DEFAULT_MODELS[slot] + ".dff"
        else:
            continue
        target = "torso" if SLOT_TEXTURE[slot] == "torso" else part_name
        entries.append((part_name, dff, target_texture(target)[0]))
    return entries


def load_weapon(gta_dir, name):
    """A weapon (or the jetpack) as a one-joint model: the joint is CJ's right hand (the upper torso for the jetpack), with an identity
    inverse bind, so the pose of that bone puts the model on him. Vertices are the weapon's own, in its model space."""
    path = Path(gta_dir) / "models" / "gta3.img"
    if not path.is_file():
        raise ConversionError(f"{path} was not found; point --gta at your GTA San Andreas folder")
    try:
        gta3 = ImgArchive(path)
        dff_data = gta3.read(f"{name}.dff")
        txd_data = gta3.read(f"{name}.txd")
    except KeyError as e:
        raise ConversionError(f"{name}: {e.args[0]}") from e
    except FormatError as e:
        raise ConversionError(f"{name}: {e}") from e
    try:
        part = parse_static_part(dff_data, children=name in ASSEMBLED)
        dictionary = parse_txd(txd_data)
    except FormatError as e:
        raise ConversionError(f"{name}: {e}") from e
    for texture, _triangles in part.groups:
        if texture not in dictionary:
            raise ConversionError(f"{name}.txd has no texture named {texture!r}; it has: {', '.join(sorted(dictionary))}")
    n = len(part.positions)
    normals = part.normals or [(0.0, 0.0, 1.0)] * n
    uvs = part.uvs or [(0.0, 0.0)] * n
    meshes, textures = [], {}
    for texture, triangles in part.groups:
        key = f"{name}:{texture}"
        used = sorted({v for tri in triangles for v in tri})
        new = {old: i for i, old in enumerate(used)}  # each mesh keeps only the vertices its triangles use
        meshes.append(MeshPart(name if len(part.groups) == 1 else f"{name}_{texture}",
                               [part.positions[v] for v in used], [normals[v] for v in used], [uvs[v] for v in used],
                               [(new[a], new[b], new[c]) for a, b, c in triangles],
                               [(0, 0, 0, 0)] * len(used), [(1.0, 0.0, 0.0, 0.0)] * len(used), key))
        textures[key] = dictionary[texture]
    identity = (1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0)
    return Model(Skeleton((ATTACH_NODES.get(name, RIGHT_HAND_NODE),), (identity,)), meshes, textures)
