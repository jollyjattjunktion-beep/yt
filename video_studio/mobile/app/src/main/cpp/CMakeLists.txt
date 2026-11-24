cmake_minimum_required(VERSION 3.22.1)
project(preet_pockettts_android LANGUAGES CXX)

add_library(preet_pockettts_jni SHARED native_smoke.cpp)
target_compile_features(preet_pockettts_jni PRIVATE cxx_std_17)
target_compile_options(preet_pockettts_jni PRIVATE -Wall -Wextra -Werror)
