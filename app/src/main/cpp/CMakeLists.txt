cmake_minimum_required(VERSION 3.22.1)
project("mastigias-taglib")

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

include(FetchContent)

# Configure TagLib static build
set(BUILD_SHARED_LIBS OFF CACHE BOOL "Build static library" FORCE)
set(ENABLE_STATIC_RUNTIME ON CACHE BOOL "Static C++ runtime" FORCE)
set(BUILD_TESTING OFF CACHE BOOL "Disable tests" FORCE)
set(BUILD_EXAMPLES OFF CACHE BOOL "Disable examples" FORCE)
set(BUILD_BINDINGS OFF CACHE BOOL "Disable bindings" FORCE)
set(WITH_ZLIB ON CACHE BOOL "Enable zlib for compressed ID3 frames" FORCE)

FetchContent_Declare(
    taglib
    URL https://taglib.org/releases/taglib-1.13.1.tar.gz
)
FetchContent_MakeAvailable(taglib)

# TagLib organizes headers in format subdirectories (flac/, mpeg/id3v2/, etc.),
# but consumers expect <taglib/*.h> flat includes as in standard installed layouts.
# We assemble a staged include directory to satisfy both <taglib/*.h> and format subdirectories.
set(TAGLIB_STAGED_INCLUDE_DIR "${CMAKE_CURRENT_BINARY_DIR}/taglib_staged_include")
file(MAKE_DIRECTORY "${TAGLIB_STAGED_INCLUDE_DIR}/taglib")

file(GLOB_RECURSE TAGLIB_ALL_HDRS
    "${taglib_SOURCE_DIR}/taglib/*.h"
    "${taglib_SOURCE_DIR}/taglib/*.tcc"
)
foreach(hdr ${TAGLIB_ALL_HDRS})
    get_filename_component(hdr_name ${hdr} NAME)
    configure_file("${hdr}" "${TAGLIB_STAGED_INCLUDE_DIR}/taglib/${hdr_name}" COPYONLY)
endforeach()

if(EXISTS "${taglib_BINARY_DIR}/taglib_config.h")
    configure_file("${taglib_BINARY_DIR}/taglib_config.h" "${TAGLIB_STAGED_INCLUDE_DIR}/taglib/taglib_config.h" COPYONLY)
endif()

add_library(mastigias-native SHARED
    taglib-bridge.cpp
)

target_include_directories(mastigias-native PRIVATE
    ${TAGLIB_STAGED_INCLUDE_DIR}
    ${taglib_SOURCE_DIR}
    ${taglib_SOURCE_DIR}/taglib
    ${taglib_SOURCE_DIR}/taglib/toolkit
    ${taglib_SOURCE_DIR}/taglib/asf
    ${taglib_SOURCE_DIR}/taglib/flac
    ${taglib_SOURCE_DIR}/taglib/mpeg
    ${taglib_SOURCE_DIR}/taglib/mpeg/id3v2
    ${taglib_SOURCE_DIR}/taglib/mpeg/id3v2/frames
    ${taglib_SOURCE_DIR}/taglib/mp4
    ${taglib_SOURCE_DIR}/taglib/ogg
    ${taglib_SOURCE_DIR}/taglib/ogg/vorbis
    ${taglib_SOURCE_DIR}/taglib/ogg/opus
    ${taglib_SOURCE_DIR}/taglib/riff
    ${taglib_SOURCE_DIR}/taglib/riff/wav
    ${taglib_SOURCE_DIR}/taglib/riff/aiff
    ${taglib_BINARY_DIR}
)

target_link_libraries(mastigias-native
    tag
    z
    log
    android
)
