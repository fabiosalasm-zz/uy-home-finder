package pe.fabiosalasm.uyhomefinder.config

import kotlinx.serialization.json.Json
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.converter.json.KotlinSerializationJsonHttpMessageConverter

@Configuration(proxyBeanMethods = false)
class KotlinSerializationHttpMessageConvertersConfiguration {

    @Bean
    fun json(): Json {
        return Json {
            isLenient = true
            ignoreUnknownKeys = true
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(Json::class)
    @ConditionalOnProperty(name = ["spring.mvc.converters.preferred-json-mapper"],
        havingValue = "kotlix-serialization", matchIfMissing = true)
    class MappingKotlinxSerialization2HttpMessageConverterConfiguration {

        @Bean
        @ConditionalOnMissingBean(KotlinSerializationJsonHttpMessageConverter::class)
        fun kotlinSerializationJsonHttpMessageConverter(json: Json): KotlinSerializationJsonHttpMessageConverter {
            return KotlinSerializationJsonHttpMessageConverter(json)
        }
    }
}