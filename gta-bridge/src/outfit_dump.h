#pragma once
#include <cmath>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <string>

namespace cj {

constexpr int kOutfitModels = 10;    // CPedClothesDesc::m_anModelKeys: torso, head, hands, legs, shoes, necklace, watch, glasses, hats, extra
constexpr int kOutfitTextures = 18;  // CPedClothesDesc::m_anTextureKeys: the layers painted over the body (shirts, jackets, tattoos...)

// What CJ wears and how his body is built, as the game keeps it: the keys are hashes of file names (see tools/cjconvert/clothes.py), fat and muscle are
// the stats (0 to 1000) that the game turns into the share of its three bodies (normal, fat, ripped).
struct Outfit {
    uint32_t models[kOutfitModels];
    uint32_t textures[kOutfitTextures];
    float fat;
    float muscle;
};

// A reading that cannot be real (a stat out of its range, or not a number) says that the layout this reads from is not the one assumed.
inline bool Plausible(const Outfit& o) {
    return std::isfinite(o.fat) && std::isfinite(o.muscle) && o.fat >= 0.0f && o.fat <= 1000.0f && o.muscle >= 0.0f && o.muscle <= 1000.0f;
}

inline bool SameOutfit(const Outfit& a, const Outfit& b) {
    return std::memcmp(&a, &b, sizeof(Outfit)) == 0;
}

// The file tools/convert_assets.py reads (--outfit): the stats and the keys as plain numbers.
inline std::string OutfitJson(const Outfit& o) {
    char number[48];
    std::string out = "{";
    std::snprintf(number, sizeof(number), "\"muscle\": %.3f, \"fat\": %.3f", o.muscle, o.fat);
    out += number;
    out += ", \"models\": [";
    for (int i = 0; i < kOutfitModels; i++) {
        std::snprintf(number, sizeof(number), i ? ", %u" : "%u", static_cast<unsigned>(o.models[i]));
        out += number;
    }
    out += "], \"textures\": [";
    for (int i = 0; i < kOutfitTextures; i++) {
        std::snprintf(number, sizeof(number), i ? ", %u" : "%u", static_cast<unsigned>(o.textures[i]));
        out += number;
    }
    out += "]}\n";
    return out;
}

// Looks at the outfit at most once a second and says when it is a new one worth writing down.
class OutfitWatcher {
public:
    static constexpr uint32_t kIntervalMs = 1000;

    bool Changed(uint32_t nowMs, const Outfit& current, std::string& json) {
        if (looked_ && nowMs - lastLookMs_ < kIntervalMs) {
            return false;
        }
        looked_ = true;
        lastLookMs_ = nowMs;
        if (!Plausible(current) || (have_ && SameOutfit(current, last_))) {
            return false;
        }
        last_ = current;
        have_ = true;
        json = OutfitJson(current);
        return true;
    }

private:
    bool looked_ = false;
    bool have_ = false;
    uint32_t lastLookMs_ = 0;
    Outfit last_{};
};

} // namespace cj
