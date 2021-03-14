package pe.fabiosalasm.uyhomefinder

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import pe.fabiosalasm.uyhomefinder.properties.AppProperties

@EnableConfigurationProperties(AppProperties::class)
@SpringBootApplication
class UyHomeFinderApplication

fun main(args: Array<String>) {
    runApplication<UyHomeFinderApplication>(*args)
}
