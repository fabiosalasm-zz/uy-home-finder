package pe.fabiosalasm.uyhomefinder.config

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.web.reactive.function.client.WebClient
import pe.fabiosalasm.uyhomefinder.properties.AppProperties
//import pe.fabiosalasm.uyhomefinder.skraper.GallitoSkraper
//import pe.fabiosalasm.uyhomefinder.skraper.InfocasasSkraper
import pe.fabiosalasm.uyhomefinder.skraper.MercadoLibreSkraper
import pe.fabiosalasm.uyhomefinder.skraper.Skraper
import pe.fabiosalasm.uyhomefinder.skraper.SkraperClient
import pe.fabiosalasm.uyhomefinder.skraper.SpringReactiveSkraperClient

@Configuration
class SkraperConfig(private val appProperties: AppProperties) {

    @Bean
    @Primary
    fun skraperClient(webClient: WebClient): SkraperClient {
        return SpringReactiveSkraperClient(webClient)
    }

    /**
    @Bean
    fun gallitoSkraper(skraperClient: SkraperClient, objectMapper: ObjectMapper): Skraper {
        require(appProperties.skrapers.containsKey("gallito")) {
            "Configuration error. Expecting app.skrapers.* property with name: gallito"
        }

        val props = appProperties.skrapers["gallito"]!!
        return GallitoSkraper(
            urlTemplate = props.urlTemplate,
            urlParams = props.urlParams,
            client = skraperClient
        )
    } **/

    /**
    @Bean
    fun infocasasSkraper(skraperClient: SkraperClient, objectMapper: ObjectMapper): Skraper {
        require(appProperties.skrapers.containsKey("infocasas")) {
            "Configuration error. Expecting app.skrapers.* property with name: infocasas"
        }

        val props = appProperties.skrapers["infocasas"]!!
        return InfocasasSkraper(
            urlTemplate = props.urlTemplate,
            urlParams = props.urlParams,
            client = skraperClient
        )
    } **/

    @Bean
    fun mercadolibreSkraper(skraperClient: SkraperClient, objectMapper: ObjectMapper): Skraper {
        require(appProperties.skrapers.containsKey("mercadolibre")) {
            "Configuration error. Expecting app.skrapers.* property with name: mercadolibre"
        }

        val props = appProperties.skrapers["mercadolibre"]!!
        return MercadoLibreSkraper(
            urlTemplate = props.urlTemplate,
            urlParams = props.urlParams,
            client = skraperClient
        )
    }
}