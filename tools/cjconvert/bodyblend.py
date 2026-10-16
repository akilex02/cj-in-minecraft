"""The build of CJ: the game does not have a muscular CJ, it blends three bodies (ripped, fat and normal, in that order in the files) by the stats."""
import math

from .dff import Part


def body_ratios(muscle, fat):
    """(ripped, fat, normal) as the game takes them from the stats of the save (CClothesBuilder, 0x5A42B0): the share of the ripped body is the muscle
    stat over 1000, the fat one is the fat stat over its 200 to 1000 range, and the normal body is what is left (they share the body if it is not enough)."""
    ripped = min(max(muscle / 1000.0, 0.0), 1.0)
    fatness = min(max((fat - 200.0) / 800.0, 0.0), 1.0)
    normal = 1.0 - ripped - fatness
    if normal <= 0.0:
        t = 1.0 / (ripped + fatness)
        ripped, fatness, normal = ripped * t, fatness * t, 0.0
    return (ripped, fatness, normal)


def blend_parts(parts, ratios):
    """One Part from the three builds of a body part: vertices, normals and texture coordinates are the weighted sum, as in the game. The triangles,
    the bones and the skin weights are those of the build that weighs the most (the three share their vertices; their weights differ little)."""
    count = len(parts[0].positions)
    if any(len(p.positions) != count for p in parts):
        raise ValueError("the builds of a body part do not have the same vertices, so they cannot be blended")
    lead = parts[max(range(len(parts)), key=lambda i: ratios[i])]

    def mix(attribute, size):
        return [tuple(sum(r * getattr(p, attribute)[v][k] for p, r in zip(parts, ratios)) for k in range(size)) for v in range(count)]

    normals = []
    for n in mix("normals", 3):
        length = math.sqrt(sum(c * c for c in n))
        normals.append(tuple(c / length for c in n) if length > 1e-9 else (0.0, 0.0, 1.0))
    return Part(mix("positions", 3), normals, mix("uvs", 2) if lead.uvs else lead.uvs, lead.triangles, lead.bone_indices, lead.weights, lead.hierarchy_ids)
