package pe.fabiosalasm.uyhomefinder.extensions

import org.springframework.web.util.UriComponentsBuilder
import java.net.URL

fun URL.cloneAndReplace(path: String = "", query: String = ""): URL {
    if (path.isNotEmpty()) {
        check(path.startsWith("/")) { "Path should start with /" }
    }

    val originalUrl = UriComponentsBuilder.fromUriString(this.toString()).build()

    val builder = UriComponentsBuilder.newInstance()
    builder.scheme(originalUrl.scheme)
    builder.port(originalUrl.port)
    builder.host(originalUrl.host)

    if (path.isNotEmpty()) {
        builder.path(path)
    } else if (!originalUrl.path.isNullOrEmpty()) {
        builder.path(originalUrl.path!!)
    }

    if (query.isNotEmpty()) {
        builder.query(query)
    } else if (!originalUrl.query.isNullOrEmpty()) {
        builder.query(originalUrl.query!!)
    }

    return URL(builder.build().toUriString())
}

fun URL.appendPath(path: String): URL {
    check(path.startsWith("/")) { "Path should start with /" }

    val originalUrl = UriComponentsBuilder.fromUriString(this.toString()).build()

    val builder = UriComponentsBuilder.newInstance()
    builder.scheme(originalUrl.scheme)
    builder.port(originalUrl.port)
    builder.host(originalUrl.host)

    if (originalUrl.path.isNullOrEmpty()) {
        builder.path(path)
    } else {
        builder.path(originalUrl.path!!.trimEnd('/') + path)
    }

    if (!originalUrl.query.isNullOrEmpty()) {
        builder.query(originalUrl.query!!)
    }

    return URL(builder.build().toUriString())
}
