package pe.fabiosalasm.uyhomefinder.properties

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import java.net.URL

@ConstructorBinding
@ConfigurationProperties(prefix = "app")
data class AppProperties(
    val skrapers: Map<String, SkraperProperty>
) {
    data class SkraperProperty(
        val urlTemplate: URL,
        val urlParams: Map<String, String>?
    )
}

