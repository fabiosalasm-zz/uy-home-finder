package pe.fabiosalasm.uyhomefinder.controller

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import pe.fabiosalasm.uyhomefinder.domain.House
import pe.fabiosalasm.uyhomefinder.service.GallitoWebPageService
import pe.fabiosalasm.uyhomefinder.service.InfoCasasWebPageService

@RestController
@RequestMapping("/rental")
class RentController(
    val gallitoWebPageService: GallitoWebPageService,
    val infoCasasWebPageService: InfoCasasWebPageService
) {

    @GetMapping("/gallito/houses")
    fun getGallitoHouses() = gallitoWebPageService.getHousesForRent()

    @GetMapping("/infocasas/houses")
    fun getInfoCasasHouses() = infoCasasWebPageService.getHousesForRent()
}