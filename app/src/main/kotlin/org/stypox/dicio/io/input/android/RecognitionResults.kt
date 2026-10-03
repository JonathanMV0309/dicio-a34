package org.stypox.dicio.io.input.android

object RecognitionResults {
    /** Android uses -1 for unknown confidence; keep its original ranking in that case. */
    fun prepare(texts: List<String>, scores: FloatArray?): List<Pair<String, Float>> {
        val validScores = scores?.takeIf { it.size == texts.size && it.all { s -> s.isFinite() && s in 0f..1f } }
        return texts.mapIndexed { index, text ->
            text.trim() to (validScores?.get(index) ?: (1f / (index + 1)))
        }.filter { it.first.isNotEmpty() }.distinctBy { it.first }.sortedByDescending { it.second }
    }
}
