package com.example

import org.junit.Assert.*
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun parseMmSsToMs_correctConversion() {
    assertEquals(0L, com.example.model.parseMmSsToMs("00:00"))
    assertEquals(15000L, com.example.model.parseMmSsToMs("00:15"))
    assertEquals(75000L, com.example.model.parseMmSsToMs("01:15"))
    assertEquals(3661000L, com.example.model.parseMmSsToMs("01:01:01"))
  }

  @Test
  fun contentPreset_allPresetsDefined() {
    val presets = com.example.model.ContentPreset.values()
    assertEquals(4, presets.size)
    assertTrue(presets.any { it.name == "GAMING" })
    assertTrue(presets.any { it.name == "PODCAST" })
    assertTrue(presets.any { it.name == "VLOG" })
    assertTrue(presets.any { it.name == "GENERAL" })
  }

  @Test
  fun extractJson_parsesMarkdownWrappedJsonAndStartEndFields() {
    val rawMarkdown = """
      Berikut hasil analisis video:
      ```json
      [
        {
          "start": "00:07",
          "end": "00:24",
          "title": "Never Let Him Go",
          "score": 0.94,
          "hook": "Wait for the reaction!",
          "reason": "Funny facial expression",
          "category": "😂 FUNNY"
        }
      ]
      ```
    """.trimIndent()

    val extracted = com.example.network.VideoAnalysisService.extractJson(rawMarkdown)
    assertTrue(extracted.startsWith("["))
    val clips = com.example.network.VideoAnalysisService.parseClipsFromJson(extracted)
    assertEquals(1, clips.size)
    assertEquals("00:07", clips[0].startStr)
    assertEquals("00:24", clips[0].endStr)
    assertEquals("Never Let Him Go", clips[0].title)
  }

  @Test
  fun extractContentFromResponseBody_handlesSseChunksAndNonStreamingJson() {
    val sseResponse = """
      data: {"id":"1","choices":[{"delta":{"content":"```json\n[\n"}}]}

      data: {"id":"1","choices":[{"delta":{"content":"  {\"start\": \"00:07\", \"end\": \"00:24\", \"title\": \"Never Let Him Go\"}\n"}}]}

      data: {"id":"1","choices":[{"delta":{"content":"]\n```"},"finish_reason":"stop"}]}

      data: [DONE]
    """.trimIndent()

    val content = com.example.network.VideoAnalysisService.extractContentFromResponseBody(sseResponse)
    val clips = com.example.network.VideoAnalysisService.parseClipsFromJson(content)
    assertEquals(1, clips.size)
    assertEquals("00:07", clips[0].startStr)
    assertEquals("00:24", clips[0].endStr)
    assertEquals("Never Let Him Go", clips[0].title)
  }
}
