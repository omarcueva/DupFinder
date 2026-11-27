package com.example

import com.example.model.FileTypeFilter
import com.example.model.MinSizePreset
import com.example.util.Formatters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateScannerLogicTest {

  @Test
  fun `file type filter matches extensions and mime types accurately`() {
    assertTrue(FileTypeFilter.ALL.matches("application/octet-stream", "anything.bin"))
    assertTrue(FileTypeFilter.IMAGES.matches("application/octet-stream", "photo.heic"))
    assertTrue(FileTypeFilter.IMAGES.matches("image/jpeg", "DSC_001"))
    assertTrue(FileTypeFilter.VIDEOS.matches("video/mp4", "clip.mp4"))
    assertTrue(FileTypeFilter.AUDIO.matches("audio/mpeg", "track.mp3"))
    assertTrue(FileTypeFilter.DOCUMENTS.matches("application/pdf", "receipt.pdf"))
    assertTrue(FileTypeFilter.DOCUMENTS.matches("text/plain", "notes.txt"))
    assertTrue(FileTypeFilter.ARCHIVES.matches("application/zip", "backup.zip"))
    assertFalse(FileTypeFilter.IMAGES.matches("application/pdf", "receipt.pdf"))
    assertFalse(FileTypeFilter.DOCUMENTS.matches("image/png", "screenshot.png"))
  }

  @Test
  fun `byte formatting and size presets produce expected values`() {
    assertEquals("0 B", Formatters.formatBytes(0L))
    assertEquals("512 B", Formatters.formatBytes(512L))
    assertEquals("10.0 KB", Formatters.formatBytes(MinSizePreset.KB_10.bytes))
    assertEquals("1.00 MB", Formatters.formatBytes(MinSizePreset.MB_1.bytes))
    assertEquals("10.0 MB", Formatters.formatBytes(MinSizePreset.MB_10.bytes))
  }
}
