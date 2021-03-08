package pe.fabiosalasm.uyhomefinder.extensions

import pe.fabiosalasm.uyhomefinder.domain.House
import pe.fabiosalasm.uyhomefinder.jooq.enums.StoreMode
import pe.fabiosalasm.uyhomefinder.jooq.tables.HouseCandidate
import pe.fabiosalasm.uyhomefinder.jooq.tables.records.HouseCandidateRecord

fun HouseCandidate.newRecord(house: House): HouseCandidateRecord {
    return this.newRecord().apply {
        sourceId = house.sourceId
        title = house.title
        link = house.link
        address = house.address
        telephone = house.telephone
        price = house.price.toString()
        department = house.department
        neighbourhood = house.neighbourhood
        description = house.description
        warranties = house.warranties.toTypedArray()
        picturelinks = house.pictureLinks.toTypedArray()
        georeference = house.geoReference?.split(",")?.toTypedArray()
        videolink = house.videoLink
        storeMode = StoreMode.valueOf(house.storeMode.name)
    }
}