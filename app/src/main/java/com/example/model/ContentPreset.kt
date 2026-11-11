package com.example.model

enum class ContentPreset(
    val title: String,
    val description: String,
    val promptFocus: String
) {
    GENERAL(
        title = "Editorial Viral",
        description = "Momen terbaik dengan retensi tinggi, narasi kuat & visual menarik",
        promptFocus = """
            - Identify the strongest, most engaging standalone moments suitable for clean, professional Reels, Shorts, and TikTok clips.
            - Focus on genuine emotional shifts, clear storytelling peaks, compelling visual actions, or natural humor.
            - Do NOT use clickbait or exaggerated emojis. Write clean, concise, insightful titles and natural narrative subtitles.
            - Category MUST be chosen from: Highlight, Insight, Story, Key Moment, Reaction.
        """.trimIndent()
    ),
    PODCAST(
        title = "Podcast & Talk",
        description = "Pernyataan tajam, argumen mendalam & kutipan berkelas",
        promptFocus = """
            - Identify articulate insights, thought-provoking statements, counter-intuitive perspectives, or sharp exchanges.
            - Write subtitles that read like clean, accurate editorial quotes or concise takeaways from the conversation.
            - Category MUST be chosen from: Insight, Perspective, Debate, Quote, Deep Dive.
        """.trimIndent()
    ),
    VLOG(
        title = "Lifestyle & Vlog",
        description = "Transisi sinematik, cerita personal & momen autentik",
        promptFocus = """
            - Identify authentic human moments, aesthetic visual transitions, surprising events, or relatable storytelling.
            - Keep captions minimal, natural, and cinematic without forced hype.
            - Category MUST be chosen from: Cinematic, Story, Candid, Travel, Highlight.
        """.trimIndent()
    ),
    GAMING(
        title = "Gaming & Stream",
        description = "Momen krusial, strategi presisi & reaksi autentik",
        promptFocus = """
            - Identify high-skill plays, decisive turning points, or genuine streamer reactions.
            - Keep titles and subtitles concise, sharp, and clean without excessive emojis.
            - Category MUST be chosen from: Clutch, Strategy, Reaction, Highlight, Turning Point.
        """.trimIndent()
    )
}
