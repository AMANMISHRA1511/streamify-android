package com.aman.streamify

data class Track(
    val id: String,
    val name: String,
    val artist: String,
    val album: String = "",
    val image: String,
    val play: String,
    val provider: String = "Streamify",
    val language: String = "",
    val year: String = "",
    val raw: String = ""
)
