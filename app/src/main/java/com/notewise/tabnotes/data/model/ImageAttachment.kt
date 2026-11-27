package com.notewise.tabnotes.data.model

import java.util.UUID

data class ImageAttachment(
    val id: String = UUID.randomUUID().toString(),
    val filePath: String,
    val x: Float = 60f,
    val y: Float = 60f,
    val width: Float = 360f,
    val height: Float = 270f,
    val rotation: Float = 0f
)
