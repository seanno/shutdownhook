#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BUILD_DIR="$SCRIPT_DIR/build"

echo "Cleaning build directory..."
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR"

echo "Running cmake..."
cmake -S "$SCRIPT_DIR" -B "$BUILD_DIR"

echo "Building..."
cmake --build "$BUILD_DIR"

echo "Done: $BUILD_DIR/minja_render"
