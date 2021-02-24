package pe.fabiosalasm.uyhomefinder.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.web.reactive.function.client.WebClient
import pe.fabiosalasm.uyhomefinder.skraper.SkraperClient
import pe.fabiosalasm.uyhomefinder.skraper.SpringReactiveSkraperClient

@Configuration
class SkraperConfig {

    @Bean
    @Primary
    fun skraperClient(webClient: WebClient): SkraperClient {
        return SpringReactiveSkraperClient(webClient)
    }
}