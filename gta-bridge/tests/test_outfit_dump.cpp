#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>

#include "outfit_dump.h"

#define CHECK(cond)                                                                        \
    do {                                                                                   \
        if (!(cond)) {                                                                     \
            std::fprintf(stderr, "%s:%d: CHECK failed: %s\n", __FILE__, __LINE__, #cond);   \
            std::exit(1);                                                                  \
        }                                                                                  \
    } while (0)

using namespace cj;

static Outfit Sample() {
    Outfit o{};
    for (int i = 0; i < kOutfitModels; i++) {
        o.models[i] = 100u + i;
    }
    for (int i = 0; i < kOutfitTextures; i++) {
        o.textures[i] = 4000000000u + i; // above the range of a signed int
    }
    o.fat = 250.5f;
    o.muscle = 1000.0f;
    return o;
}

int main() {
    { // the json holds the stats and every key, as plain numbers
        const std::string json = OutfitJson(Sample());
        CHECK(json.find("\"muscle\": 1000.000") != std::string::npos);
        CHECK(json.find("\"fat\": 250.500") != std::string::npos);
        CHECK(json.find("\"models\": [100, 101, 102, 103, 104, 105, 106, 107, 108, 109]") != std::string::npos);
        CHECK(json.find("4000000000, 4000000001") != std::string::npos);
        CHECK(json.find("4000000017]") != std::string::npos); // the eighteenth texture key, the last
        CHECK(json.front() == '{' && json.back() == '\n');
    }

    { // the watcher reports the first reading, then only changes, and no more than once a second
        OutfitWatcher w;
        std::string json;
        Outfit o = Sample();
        CHECK(w.Changed(1000, o, json) && !json.empty());     // the first reading
        CHECK(!w.Changed(1500, o, json));                      // too soon
        CHECK(!w.Changed(2500, o, json));                      // the same outfit
        o.muscle = 0.0f;
        CHECK(!w.Changed(2600, o, json));                      // changed but read too soon after the last look
        CHECK(w.Changed(3600, o, json));                       // changed
        CHECK(json.find("\"muscle\": 0.000") != std::string::npos);
        o.textures[3] += 1;
        CHECK(w.Changed(4700, o, json));                       // a texture key changed
        o.models[9] = 0;
        CHECK(w.Changed(5800, o, json));                       // a model went off
    }

    { // a broken reading (a stat out of any range) is not written: the layout assumption is wrong
        OutfitWatcher w;
        std::string json;
        Outfit o = Sample();
        o.muscle = 5.0e9f;
        CHECK(!Plausible(o));
        CHECK(!w.Changed(1000, o, json));
        o = Sample();
        CHECK(Plausible(o));
        o.fat = -3.0f;
        CHECK(!Plausible(o));
    }

    std::puts("test_outfit_dump OK");
    return 0;
}
