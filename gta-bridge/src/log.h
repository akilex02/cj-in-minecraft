#pragma once
#include <cstdarg>
#include <cstdio>

namespace cj {

// Appends a line to cj_bridge.log in the game directory (the process working directory).
inline void Log(const char* fmt, ...) {
    static FILE* file = std::fopen("cj_bridge.log", "a");
    if (!file) {
        return;
    }
    va_list args;
    va_start(args, fmt);
    std::vfprintf(file, fmt, args);
    va_end(args);
    std::fputc('\n', file);
    std::fflush(file);
}

} // namespace cj
