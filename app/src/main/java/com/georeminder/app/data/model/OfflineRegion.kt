package com.georeminder.app.data.model

data class OfflineRegion(
    val id: String,
    val name: String,
    val minZoom: Int,
    val maxZoom: Int,
    val tileCount: Int,
    val sizeMb: Double,
    val downloadedAt: String
)
