package pe.fabiosalasm.uyhomefinder.domain

data class Post(
    val link: String,
    val hasGPS: Boolean = false,
    val hasVideo: Boolean = false
)