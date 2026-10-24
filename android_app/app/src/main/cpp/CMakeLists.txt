cmake_minimum_required(VERSION 3.22.1)
project("ddmc_native")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}/../../../../../ddmc_cpp/include
)

add_library(ddmc_native SHARED
    native_bridge.cpp
    ../../../../../ddmc_cpp/src/ddmc_fft.cpp
    ../../../../../ddmc_cpp/src/ddmc_companion.cpp
    ../../../../../ddmc_cpp/src/ddmc_lyrics.cpp
)

find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(ddmc_native
    ${log-lib}
    ${android-lib}
)

target_link_options(ddmc_native PRIVATE "-Wl,-z,max-page-size=16384")
