cmake_minimum_required(VERSION 3.20)
project(cj_bridge CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(cj_bridge SHARED src/dllmain.cpp src/hook.cpp src/bridge.cpp)
target_include_directories(cj_bridge PRIVATE src)
target_compile_options(cj_bridge PRIVATE -Wall -Wextra)
target_link_libraries(cj_bridge PRIVATE ws2_32)
target_link_options(cj_bridge PRIVATE -static -static-libgcc -static-libstdc++)
set_target_properties(cj_bridge PROPERTIES PREFIX "" SUFFIX ".asi")
