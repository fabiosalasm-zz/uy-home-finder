package pe.fabiosalasm.uyhomefinder.config

import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfigureBefore
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.autoconfigure.condition.NoneNestedConditions
import org.springframework.boot.autoconfigure.http.HttpMessageConverters
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Conditional
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.ConfigurationCondition
import org.springframework.context.annotation.Import
import org.springframework.http.converter.HttpMessageConverter
import kotlin.streams.toList

@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(HttpMessageConverter::class)
@Conditional(CustomHttpMessageConvertersAutoConfiguration.NotReactiveWebApplicationCondition::class)
@AutoConfigureBefore(HttpMessageConvertersAutoConfiguration::class)
@Import(KotlinSerializationHttpMessageConvertersConfiguration::class)
class CustomHttpMessageConvertersAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    fun messageConverters(converters: ObjectProvider<HttpMessageConverter<*>>): HttpMessageConverters {
        return HttpMessageConverters(converters.orderedStream().toList())
    }

    class NotReactiveWebApplicationCondition: NoneNestedConditions(ConfigurationCondition.ConfigurationPhase.PARSE_CONFIGURATION) {
        @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
        private class ReactiveWebApplication {}
    }

}