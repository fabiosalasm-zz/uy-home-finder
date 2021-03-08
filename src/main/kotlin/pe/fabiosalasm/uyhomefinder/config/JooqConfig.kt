package pe.fabiosalasm.uyhomefinder.config

import org.jooq.conf.Settings
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class JooqConfig {

    @Bean
    fun jooqSettings(): Settings {
        return Settings().apply {
            withExecuteLogging(false)
        }
    }
}