package com.notewise.tabnotes.data.model

enum class PageTemplate(val displayName: String) {
    BLANK("Blank Paper"),
    LINED("Lined / Ruled"),
    GRID("Grid / Graph"),
    DOTTED("Dot Matrix"),
    CORNELL("Cornell Notes"),
    TASK_LIST("Task Planner")
}

enum class PaperColor(val hex: Long, val displayName: String) {
    WHITE(0xFFFFFFFF, "Pure White"),
    IVORY(0xFFFAF7EE, "Warm Ivory"),
    SEPIA(0xFFF4ECD8, "Vintage Sepia"),
    DARK_GRAPHITE(0xFF22252A, "Graphite Dark"),
    MIDNIGHT(0xFF14181F, "Midnight Blue")
}

enum class PaperSize(
    val displayName: String,
    val widthRatio: Float,
    val heightRatio: Float,
    val widthDp: Float,
    val heightDp: Float
) {
    A4("A4 (210 x 297 mm)", 1.0f, 1.414f, 820f, 1160f),
    A3("A3 (297 x 420 mm)", 1.0f, 1.414f, 1160f, 1640f),
    LETTER("US Letter (8.5 x 11 in)", 1.0f, 1.294f, 820f, 1060f),
    LEGAL("US Legal (8.5 x 14 in)", 1.0f, 1.647f, 820f, 1350f),
    STANDARD("Infinite Canvas", 1.0f, 1.333f, 960f, 1280f);

    val aspectRatio: Float get() = heightRatio / widthRatio
}
