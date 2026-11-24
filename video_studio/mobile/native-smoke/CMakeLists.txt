cmake_minimum_required(VERSION 3.22.1)
project(preet_pockettts_native_smoke LANGUAGES CXX)

add_library(preet_pockettts_native_smoke SHARED native_smoke.cpp)
target_compile_features(preet_pockettts_native_smoke PRIVATE cxx_std_17)
target_compile_options(preet_pockettts_native_smoke PRIVATE -Wall -Wextra -Werror)
