cmake_minimum_required(VERSION 3.22.1)
project(phase_audio)

find_package(oboe REQUIRED CONFIG)

add_library(
    phase_audio
    SHARED
    AudioEngine.cpp
    JniBridge.cpp
)

target_compile_features(phase_audio PRIVATE cxx_std_20)
target_compile_options(phase_audio PRIVATE -Wall -Wextra -Werror)
target_link_libraries(phase_audio oboe::oboe android log)
