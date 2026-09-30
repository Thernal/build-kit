package io.thernal.buildkit.fixture.core.config

/** What the rest of the fixture reads instead of the generated [Environment] directly. */
object AppConfig {
    val flavor: String get() = Environment.FLAVOR
    val baseUrl: String get() = Environment.BASE_URL
    val isProduction: Boolean get() = Environment.IS_PRODUCTION
}
