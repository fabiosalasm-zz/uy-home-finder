package pe.fabiosalasm.uyhomefinder.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.web.reactive.function.client.WebClient
import pe.fabiosalasm.uyhomefinder.properties.AppProperties
import pe.fabiosalasm.uyhomefinder.skraper.GallitoSkraper
import pe.fabiosalasm.uyhomefinder.skraper.MercadoLibreSkraper
import pe.fabiosalasm.uyhomefinder.skraper.Skraper
import pe.fabiosalasm.uyhomefinder.skraper.SkraperClient
import pe.fabiosalasm.uyhomefinder.skraper.SpringReactiveSkraperClient
import reactor.core.scheduler.Scheduler
import reactor.core.scheduler.Schedulers

@Configuration
class SkraperConfig(private val appProperties: AppProperties) {

    @Bean
    fun webClientScheduler(): Scheduler {
        return Schedulers.newBoundedElastic(5, 10, "MyThreadGroup")
    }

    @Bean
    @Primary
    fun skraperClient(webClient: WebClient, webClientScheduler: Scheduler): SkraperClient {
        return SpringReactiveSkraperClient(webClient, webClientScheduler)
    }

    @Bean
    fun gallitoSkraper(skraperClient: SkraperClient, webClientScheduler: Scheduler): Skraper {
        require(appProperties.skrapers.containsKey("gallito")) {
            "Configuration error. Expecting 'app.skrapers' property 'gallito' in configuration file"
        }

        val props = appProperties.skrapers["gallito"]!!
        return GallitoSkraper(
            urlTemplate = props.urlTemplate,
            urlParams = props.urlParams,
            client = skraperClient
        )
    }

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
    fun mercadolibreSkraper(skraperClient: SkraperClient, webClientScheduler: Scheduler): Skraper {
        require(appProperties.skrapers.containsKey("mercadolibre")) {
            "Configuration error. Expecting 'app.skrapers' with property 'mercadolibre' in configuration file"
        }

        val skraperConfig = appProperties.skrapers["mercadolibre"]!!
        return MercadoLibreSkraper(
            urlTemplate = skraperConfig.urlTemplate,
            urlParams = skraperConfig.urlParams,
            client = skraperClient
        )
    }
}