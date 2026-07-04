cmake_minimum_required(VERSION 3.14)
project(minja_render CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

include(FetchContent)

# Fetch nlohmann_json under the name "json" — the same name minja uses
# internally — so CMake's FetchContent deduplication prevents a duplicate target.
FetchContent_Declare(
    json
    GIT_REPOSITORY https://github.com/nlohmann/json.git
    GIT_TAG        v3.11.3
)
set(JSON_BuildTests OFF CACHE INTERNAL "")
set(JSON_Install    OFF CACHE INTERNAL "")
FetchContent_MakeAvailable(json)

FetchContent_Declare(
    minja
    GIT_REPOSITORY https://github.com/google/minja.git
    GIT_TAG        main
)
set(MINJA_TEST_ENABLED OFF CACHE BOOL "" FORCE)
set(MINJA_EXAMPLE_ENABLED OFF CACHE BOOL "" FORCE)
FetchContent_MakeAvailable(minja)

add_executable(minja_render main.cpp)
target_link_libraries(minja_render PRIVATE nlohmann_json::nlohmann_json)
target_include_directories(minja_render PRIVATE ${minja_SOURCE_DIR}/include)
