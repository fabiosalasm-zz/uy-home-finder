package pe.fabiosalasm.uyhomefinder.controller

import mu.KotlinLogging
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import pe.fabiosalasm.uyhomefinder.dto.json.HouseDto
import pe.fabiosalasm.uyhomefinder.service.RentService
import javax.validation.Valid

@RestController
@RequestMapping("/rental")
class RentController(
    private val rentService: RentService
) {

    private companion object {
        val logger = KotlinLogging.logger {  }
    }

    @GetMapping("/import")
    fun importHouses(@RequestParam("from") from: String) {
        when (from) {
            "all" -> rentService.importHousesFromAllSkrapers()
            else -> rentService.importHousesFrom(skraperName = from)
        }
    }

    @PostMapping("/save")
    fun saveHouse(@RequestBody @Valid houseDto: HouseDto) {
        logger.info { "Received: $houseDto" }
    }
}

