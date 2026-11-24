package com.preetsports.studio

/**
 * Minimal JNI loader/probe used to verify that the app packages its native library.
 * This is build plumbing only; it does not run PocketTTS inference.
 */
internal object NativeBuildSmoke {
    init {
        System.loadLibrary("preet_pockettts_jni")
    }

    @JvmStatic
    external fun nativeAbiWordSize(): Int
}
