package com.preetsports.studio

/**
 * Metadata for Mithi v2 Flash. This is deliberately separate from the Sherpa/VITS
 * model catalog: Mithi's ONNX export has a multi-graph pipeline and requires its
 * own tokenizer, phonemizer, text-normalisation and inference adapter.
 */
data class MithiVoice(
    val id: String,
    val displayName: String,
    val gender: String
)

object MithiV2FlashCatalog {
    const val MODEL_REPOSITORY = "Oomni/mithi-v2-flash"
    const val ONNX_REPOSITORY = "AXEUS6354/mithi-v2-flash-onnx"
    const val LICENSE = "Apache-2.0"
    const val SAMPLE_RATE = 24000

    val supportedLanguages = listOf(
        "hi", "en", "bn", "gu", "kn", "ml", "mr", "or", "pa", "ta", "te"
    )

    val voices = listOf(
        MithiVoice("satvik", "Satvik", "Male"),
        MithiVoice("anirban", "Anirban", "Male"),
        MithiVoice("nakul", "Nakul", "Male"),
        MithiVoice("vasant", "Vasant", "Male"),
        MithiVoice("siddhant", "Siddhant", "Male"),
        MithiVoice("charvi", "Charvi", "Female"),
        MithiVoice("ahana", "Ahana", "Female"),
        MithiVoice("vaidehi", "Vaidehi", "Female"),
        MithiVoice("malini", "Malini", "Female"),
        MithiVoice("jahnavi", "Jahnavi", "Female")
    )

    /**
     * False until the complete ONNX graph pipeline and frontend have been
     * implemented and validated on a physical Android device.
     */
    const val ANDROID_SYNTHESIS_READY = false
}
