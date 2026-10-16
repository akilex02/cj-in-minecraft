"""What CJ wears and how his body is built, as the bridge writes it down (cj_outfit.json): the stats and the keys of the clothes of the game."""
import json
import math
from dataclasses import dataclass

MODEL_SLOTS = ("torso", "head", "hands", "legs", "shoes", "necklace", "watch", "glasses", "hats", "extra")
TEXTURE_SLOTS = ("torso", "head", "legs", "shoes", "lower left arm", "upper left arm", "upper right arm", "lower right arm", "back top", "left chest",
                 "right chest", "stomach", "upper back", "necklace", "watch", "glasses", "hats", "extra")


@dataclass(frozen=True)
class Outfit:
    muscle: float
    fat: float
    models: tuple    # the key of the model of each of MODEL_SLOTS (0 = none)
    textures: tuple  # the key of the texture of each of TEXTURE_SLOTS (0 = none)


def _table():
    table = []
    for n in range(256):
        c = n
        for _ in range(8):
            c = (c >> 1) ^ 0xEDB88320 if c & 1 else c >> 1
        table.append(c)
    return table


_TABLE = _table()


def uppercase_key(name):
    """CKeyGen::GetUppercaseKey: the CRC-32 (reflected, 0xEDB88320) of the name in capitals, starting from all ones and without the final inversion."""
    key = 0xFFFFFFFF
    for byte in name.upper().encode("latin-1"):
        key = _TABLE[(byte ^ key) & 0xFF] ^ (key >> 8)
    return key


def load_outfit(path):
    """The outfit of a cj_outfit.json; ValueError (with what is wrong) if it is not the shape the bridge writes."""
    try:
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
    except OSError as e:
        raise ValueError(f"{path} could not be read: {e}") from e
    except ValueError as e:
        raise ValueError(f"{path} is not JSON: {e}") from e
    if not isinstance(data, dict):
        raise ValueError(f"{path} is not an outfit")
    stats = []
    for field in ("muscle", "fat"):
        value = data.get(field)
        if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value) or not 0 <= value <= 1000:
            raise ValueError(f"{path}: {field} must be a number from 0 to 1000, not {value!r}")
        stats.append(float(value))
    keys = []
    for field, count in (("models", len(MODEL_SLOTS)), ("textures", len(TEXTURE_SLOTS))):
        value = data.get(field)
        if (not isinstance(value, list) or len(value) != count
                or not all(isinstance(k, int) and not isinstance(k, bool) and 0 <= k <= 0xFFFFFFFF for k in value)):
            raise ValueError(f"{path}: {field} must be {count} keys")
        keys.append(tuple(value))
    return Outfit(stats[0], stats[1], keys[0], keys[1])


def name_keys(keys, names):
    """The name of each key (None for an empty slot, '?<key>' when no file of the game has that name)."""
    return [None if k == 0 else names.get(k, f"?{k}") for k in keys]


def resolve_stats(outfit_path, muscle, fat):
    """(muscle, fat) for the body: those of the dump, with what is asked by hand over them; None when nothing is asked (the normal body)."""
    if outfit_path is None and muscle is None and fat is None:
        return None
    base = load_outfit(outfit_path) if outfit_path is not None else Outfit(0.0, 0.0, (), ())
    values = []
    for name, asked, own in (("muscle", muscle, base.muscle), ("fat", fat, base.fat)):
        value = own if asked is None else asked
        if not isinstance(value, (int, float)) or not math.isfinite(value) or not 0 <= value <= 1000:
            raise ValueError(f"{name} must be a number from 0 to 1000, not {value!r}")
        values.append(float(value))
    return (values[0], values[1])


def key_tables(file_names, textures_of):
    """({key: model name}, {key: 'txd:texture'}) for the files of player.img. The game keys a model by the name of its .dff and a texture by the name of its
    .txd (a cap's capblkback.txd holds a texture called capblk); `textures_of(txd file name)` lists the textures inside, and raises for one that cannot be read
    (it is left out)."""
    models, textures = {}, {}
    for file in file_names:
        stem, _, extension = file.rpartition(".")
        extension = extension.lower()
        if extension == "dff":
            models[uppercase_key(stem)] = stem
        elif extension == "txd":
            try:
                inside = list(textures_of(file))
            except Exception:  # a texture dictionary of the game that this reader cannot decode
                continue
            textures[uppercase_key(stem)] = f"{stem}:{','.join(inside)}"
    return models, textures
