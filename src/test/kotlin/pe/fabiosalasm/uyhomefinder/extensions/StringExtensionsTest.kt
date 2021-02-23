package pe.fabiosalasm.uyhomefinder.extensions

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.javamoney.moneta.Money
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.math.BigDecimal
import java.text.ParseException
import javax.money.Monetary
import javax.money.UnknownCurrencyException

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StringExtensionsTest {

    @Nested
    inner class ToMoneyExtension {
        @Test
        fun `should fail when string cannot be parsed`() {
            val actualException = shouldThrow<IllegalArgumentException> {
                "EXT_1000".toMoney()
            }

            actualException.message shouldBe "Cannot parse string: EXT_1000"
        }

        @Test
        fun `should fail when currency is unknown`() {
            shouldThrow<UnknownCurrencyException> {
                "EXT 1000".toMoney()
            }
        }

        @Test
        fun `should fail when amount is invalid`() {
            shouldThrow<ParseException> {
                "USD XXXX".toMoney()
            }
        }

        @Test
        fun `should pass when currency and amount are valid`() {
            val actualResult = "USD 1.000".toMoney()
            val expectedResult = Money.of(BigDecimal("1000"), Monetary.getCurrency("USD"))
            actualResult shouldBe expectedResult
        }
    }
}