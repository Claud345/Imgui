cmake_minimum_required(VERSION 3.22.1)
project(imgui_overlay CXX)

# Fetch official Dear ImGui repository during build
include(FetchContent)
FetchContent_Declare(
    imgui
    GIT_REPOSITORY https://github.com/ocornut/imgui.git
    GIT_TAG        v1.90.4
)
FetchContent_MakeAvailable(imgui)

# Define native library source files
add_library(imgui_overlay SHARED
    native_imgui.cpp
    ${imgui_SOURCE_DIR}/imgui.cpp
    ${imgui_SOURCE_DIR}/imgui_draw.cpp
    ${imgui_SOURCE_DIR}/imgui_widgets.cpp
    ${imgui_SOURCE_DIR}/imgui_tables.cpp
    ${imgui_SOURCE_DIR}/backends/imgui_impl_opengl3.cpp
)

# Target include directories
target_include_directories(imgui_overlay PRIVATE
    ${imgui_SOURCE_DIR}
    ${imgui_SOURCE_DIR}/backends
)

# Compile flags
target_compile_definitions(imgui_overlay PRIVATE
    IMGUI_IMPL_OPENGL_ES3
)

# Link essential Android and OpenGL graphics libraries
target_link_libraries(imgui_overlay
    GLESv3
    EGL
    android
    log
)
