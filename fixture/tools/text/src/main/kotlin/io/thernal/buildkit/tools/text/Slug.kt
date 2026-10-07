package io.thernal.buildkit.tools.text

private val SEPARATORS = Regex("[^a-z0-9]+")

/** `Venue Management` → `venue-management`: the kind of helper a code generator needs. */
fun slug(text: String): String {
    return text.trim()
        .lowercase()
        .split(SEPARATORS)
        .filter(String::isNotEmpty)
        .joinToString("-")
}
