package pe.fabiosalasm.uyhomefinder

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import pe.fabiosalasm.uyhomefinder.extensions.appendPath
import pe.fabiosalasm.uyhomefinder.extensions.cloneAndReplace
import java.net.URL

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UrlExtensionsTest {

    @Nested
    inner class CloneAndReplaceExtension {
        @Test
        fun `should fail when passing a path without back slash`() {
            val url = URL("https://localhost:8080")
            val actualException = shouldThrow<IllegalStateException> {
                url.cloneAndReplace(path = "random")
            }

            actualException.message shouldBe "Path should start with /"
        }

        //TODO: include more tests
    }

    @Nested
    inner class AppendPathExtension {
        @Test
        fun `should fail when passing a path without back slash`() {
            val url = URL("https://localhost:8080")
            val actualException = shouldThrow<IllegalStateException> {
                url.appendPath("random")
            }

            actualException.message shouldBe "Path should start with /"
        }

        @Test
        fun `should pass when passing a valid path`() {
            val url = URL("https://localhost:8080")
            val actualResult = url.appendPath("/random")

            actualResult.toString() shouldBe "https://localhost:8080/random"
        }

        @Test
        fun `should pass when passing a valid path and respect path ordering`() {
            val url = URL("https://localhost:8080/test")
            val actualResult = url.appendPath("/random")

            actualResult.toString() shouldBe "https://localhost:8080/test/random"
        }

        @Test
        fun `should pass when passing a valid path and ignore back slashes at end`() {
            val url = URL("https://localhost:8080/")
            val actualResult = url.appendPath("/random")

            actualResult.toString() shouldBe "https://localhost:8080/random"
        }

        @Test
        fun `should pass when passing a valid path and include query params`() {
            val url = URL("https://localhost:8080/test?q=2")
            val actualResult = url.appendPath("/random")

            actualResult.toString() shouldBe  "https://localhost:8080/test/random?q=2"
        }
    }



}