package com.lifeos.secondbrain.sync

/** Pure path policy kept separate so Drive folder resolution can be tested without network access. */
object CapturePathPolicy {
    enum class Location { INBOX, ROOT }

    fun relativePath(location: Location, folderName: String, fileName: String): String = when (location) {
        Location.INBOX -> "00 Inbox/$folderName/$fileName"
        Location.ROOT -> "$folderName/$fileName"
    }
}
