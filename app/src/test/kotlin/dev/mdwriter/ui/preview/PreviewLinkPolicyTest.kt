package dev.mdwriter.ui.preview

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Acceptance 3. */
class PreviewLinkPolicyTest {
    @Test
    fun javascriptSchemeIsBlocked() {
        assertThat(PreviewLinkPolicy.decide("javascript:alert(1)")).isEqualTo(LinkAction.Block)
    }

    @Test
    fun baseUrlWithNoFragmentIsBlocked() {
        // A sanitized `javascript:` link becomes href="", which navigates back to the base URL with no fragment.
        assertThat(PreviewLinkPolicy.decide("https://appassets.androidplatform.net/doc/")).isEqualTo(LinkAction.Block)
    }

    @Test
    fun ownHostFragmentIsInPage() {
        assertThat(PreviewLinkPolicy.decide("https://appassets.androidplatform.net/doc/#fn-1"))
            .isEqualTo(LinkAction.InPage)
    }

    @Test
    fun httpAndHttpsGoExternal() {
        assertThat(PreviewLinkPolicy.decide("https://example.com")).isEqualTo(LinkAction.External)
    }

    @Test
    fun mailtoGoesExternal() {
        assertThat(PreviewLinkPolicy.decide("mailto:a@b.c")).isEqualTo(LinkAction.External)
    }

    @Test
    fun intentSchemeIsBlocked() {
        assertThat(PreviewLinkPolicy.decide("intent://x#Intent;end")).isEqualTo(LinkAction.Block)
    }

    @Test
    fun fileSchemeIsBlocked() {
        assertThat(PreviewLinkPolicy.decide("file:///etc/hosts")).isEqualTo(LinkAction.Block)
    }

    @Test
    fun contentSchemeIsBlocked() {
        assertThat(PreviewLinkPolicy.decide("content://x")).isEqualTo(LinkAction.Block)
    }

    @Test
    fun relativeNoteLinkResolvedAgainstDocIsBlocked() {
        assertThat(PreviewLinkPolicy.decide("https://appassets.androidplatform.net/doc/other.md"))
            .isEqualTo(LinkAction.Block)
    }
}
