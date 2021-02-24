package pe.fabiosalasm.uyhomefinder.domain

data class Post(
    var link: String = "no-link",
    var hasGPS: Boolean = false,
    var hasVideo: Boolean = false
)