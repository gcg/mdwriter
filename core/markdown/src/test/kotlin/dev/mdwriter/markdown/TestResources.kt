package dev.mdwriter.markdown

/** Reads a UTF-8 test resource from the classpath (e.g. `spec-cases.txt`). */
internal fun resourceText(name: String): String {
    val stream =
        object {}.javaClass.classLoader.getResourceAsStream(name)
            ?: error("resource not found: $name")
    return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
}
