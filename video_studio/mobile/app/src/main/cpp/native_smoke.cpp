#include <jni.h>

extern "C" JNIEXPORT jint JNICALL
Java_com_preetsports_studio_NativeBuildSmoke_nativeAbiWordSize(JNIEnv*, jclass) {
    return static_cast<jint>(sizeof(void*) * 8);
}
