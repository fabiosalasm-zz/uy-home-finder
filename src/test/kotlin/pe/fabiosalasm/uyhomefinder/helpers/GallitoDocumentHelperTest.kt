package pe.fabiosalasm.uyhomefinder.helpers

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import org.jsoup.Jsoup
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.io.File

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GallitoDocumentHelperTest {

    @Test
    fun `should pass when bedroom html tag is found and contains valid text`() {
        val input = File("src/test/resources/house_details.html")
        val document = Jsoup.parse(input, null)
        val element = document.selectFirst("div.wrapperDatos div.iconoDatos.rounded-circle i.fas.fa-bed")
        element shouldNotBe null
        element.hasParent() shouldBe true

        val bedroomInfoElement = element.parent()!!.nextElementSibling()
        bedroomInfoElement shouldNotBe null
        bedroomInfoElement.tagName() shouldBe "p"

        bedroomInfoElement.ownText() shouldContain """\d dormitorios""".toRegex()
    }
}