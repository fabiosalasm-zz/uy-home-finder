package pe.fabiosalasm.uyhomefinder.config

import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.ExchangeStrategies
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import java.time.Duration

@Configuration
class WebClientConfig {

    @Bean
    @Primary
    fun webClient(): WebClient {
        return WebClient
            .builder()
            .clientConnector(ReactorClientHttpConnector(
                HttpClient
                    .create()
                    .responseTimeout(Duration.ofSeconds(30))
                    .followRedirect(true)
                    .secure {
                        it.sslContext(
                            SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build()
                        )
                    }
            ))
            .exchangeStrategies(
                ExchangeStrategies
                    .builder()
                    .codecs {
                        it.defaultCodecs().apply {
                            maxInMemorySize(-1)
                            //jackson2JsonDecoder(Jackson2JsonDecoder(mapper))
                            //jackson2JsonEncoder(Jackson2JsonEncoder(mapper))
                        }
                    }
                    .build()
            )
            .build()
    }
}