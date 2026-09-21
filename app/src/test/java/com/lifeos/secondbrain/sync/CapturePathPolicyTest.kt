package com.lifeos.secondbrain.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class CapturePathPolicyTest {
    @Test
    fun nestedInboxPathMatchesDrivePlacement() {
        assertEquals(
            "00 Inbox/Quick Capture/2026-09-19 18-00-00.md",
            CapturePathPolicy.relativePath(
                CapturePathPolicy.Location.INBOX,
                "Quick Capture",
                "2026-09-19 18-00-00.md"
            )
        )
    }

    @Test
    fun existingRootFolderDoesNotPretendToBeInsideInbox() {
        assertEquals(
            "Voice Inbox/2026-09-19 18-00-00.md",
            CapturePathPolicy.relativePath(
                CapturePathPolicy.Location.ROOT,
                "Voice Inbox",
                "2026-09-19 18-00-00.md"
            )
        )
    }
}
