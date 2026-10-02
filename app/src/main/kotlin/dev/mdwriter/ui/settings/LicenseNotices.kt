package dev.mdwriter.ui.settings

import androidx.annotation.StringRes
import dev.mdwriter.R

/** One entry of the About licence list; the full text lives in `assets/licenses/` (the app has no network). */
data class LicenseNotice(
    @StringRes val titleRes: Int,
    val component: String,
    val assetPath: String,
)

val LicenseNotices =
    listOf(
        LicenseNotice(R.string.about_license_ofl, "iA Writer Duo, Quattro, Mono", "licenses/iA-Writer-fonts-OFL.txt"),
        LicenseNotice(
            R.string.about_license_commonmark,
            "commonmark-java",
            "licenses/commonmark-java-BSD-2-Clause.txt",
        ),
        LicenseNotice(R.string.about_license_autolink, "autolink-java", "licenses/autolink-java-MIT.txt"),
        LicenseNotice(R.string.about_license_apache, "AndroidX, Kotlin, kotlinx.coroutines", "licenses/Apache-2.0.txt"),
    )
