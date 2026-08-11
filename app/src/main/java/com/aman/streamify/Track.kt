package com.aman.streamify

data class Track(
    val id: String,
    val name: String,
    val artist: String,
    val image: String,
    val play: String,
    val provider: String,
    val raw: String = ""
)