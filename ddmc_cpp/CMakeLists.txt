cmake_minimum_required(VERSION 3.16)
project(DDMC_CPP VERSION 1.0.0 LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

include_directories(include)

set(DDMC_SOURCES
    src/main.cpp
    src/ddmc_audio.cpp
    src/ddmc_fft.cpp
    src/ddmc_lyrics.cpp
    src/ddmc_companion.cpp
    src/ddmc_renderer.cpp
)

add_executable(ddmc ${DDMC_SOURCES})

if(WIN32)
    target_link_libraries(ddmc PRIVATE winmm ole32)
elseif(ANDROID)
    target_link_libraries(ddmc PRIVATE OpenSLES log m)
elseif(UNIX)
    find_package(Threads REQUIRED)
    target_link_libraries(ddmc PRIVATE Threads::Threads ${CMAKE_DL_LIBS} m)
endif()

# Compiler optimizations
if(MSVC)
    target_compile_options(ddmc PRIVATE /O2 /W3 /utf-8)
else()
    target_compile_options(ddmc PRIVATE -O3 -Wall -Wextra)
endif()
