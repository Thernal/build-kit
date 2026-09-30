package io.thernal.buildkit.fixture.tools.text

/** `Venue Management` → `venue-management`: the kind of helper a code generator needs. */
fun slug(text: String): String = text.trim().lowercase().split(Regex("[^a-z0-9]+")).filter(String::isNotEmpty).joinToString("-")
