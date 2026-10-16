"""Dressing CJ like the game does (CClothesBuilder): the textures of what he wears are put over the skin of his body, layer by layer."""
from .errors import ConversionError
from .txd import Texture


def place(base, over):
    """The texture `over` placed on `base` the way the game does (PlaceTextureOnTopOfTexture): every pixel of `over` with some alpha replaces the one
    below as a whole, without blending, and the transparent ones leave it. Both must have the same size."""
    if (base.width, base.height) != (over.width, over.height):
        raise ConversionError(f"cannot place {over.name} ({over.width}x{over.height}) over {base.name} ({base.width}x{base.height})")
    out = bytearray(base.rgba)
    for i in range(0, len(out), 4):
        if over.rgba[i + 3]:
            out[i:i + 4] = over.rgba[i:i + 4]
    return Texture(f"{base.name}+{over.name}", base.width, base.height, bytes(out))


def blend_skin(skins, ratios):
    """The skin of the torso for a build: the three skins (ripped, fat, normal) weighted by the ratios, colour only; the alpha is the first one's."""
    first = skins[0]
    for skin in skins:
        if (skin.width, skin.height) != (first.width, first.height):
            raise ConversionError(f"the skins of the body are not of one size ({skin.name} is {skin.width}x{skin.height}, {first.name} {first.width}x{first.height})")
    out = bytearray(first.rgba)
    for i in range(0, len(out), 4):
        for c in range(3):
            out[i + c] = min(255, int(sum(r * s.rgba[i + c] for s, r in zip(skins, ratios)) + 0.5))
    return Texture("+".join(s.name for s in skins), first.width, first.height, bytes(out))


# The ten model slots of the game (CPedClothesDesc::m_anModelKeys), the part each one makes, and the texture each part is painted with (the material
# names of CClothesBuilder: the hands share the torso's). The first five always exist: an empty slot is the bare body part.
SLOT_PARTS = ("torso", "head", "hands", "legs", "feet", "necklace", "watch", "glasses", "hats", "extra")
SLOT_TEXTURE = ("torso", "head", "torso", "legs", "feet", "necklace", "watch", "glasses", "hat", "extra")
DEFAULT_MODELS = ("torso", "head", "hands", "legs", "feet")
# The eighteen texture slots (CPedClothesDesc::m_anTextureKeys): these go over the skin of the torso, in this order (the shirt, the tattoos of the arms,
# the chest, the stomach and the back); the others are the texture of the part of their own.
TORSO_LAYERS = (0, 4, 5, 6, 7, 8, 9, 10, 11, 12)
OWN_TEXTURE_SLOT = {"head": 1, "legs": 2, "feet": 3, "necklace": 13, "watch": 14, "glasses": 15, "hats": 16, "extra": 17}
# What a bare part is painted with when its slot names no texture: the skins of the game (the face is the one of player_face.txd, not the 8-bit head)
BARE_TEXTURES = {"head": ("player_face.txd", "face"), "legs": ("player_legs.txd", None), "feet": ("player_feet.txd", None)}
