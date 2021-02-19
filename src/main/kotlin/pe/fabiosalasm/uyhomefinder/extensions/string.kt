package pe.fabiosalasm.uyhomefinder.extensions

import org.javamoney.moneta.Money
import java.text.NumberFormat
import java.util.Locale
import javax.money.Monetary
import javax.money.MonetaryException

fun String.toMoney(): Money {
    require(this.split(" ").size == 2) { "Invalid money format" }
    val x = this.split(" ")
    return Money.of(
        NumberFormat.getInstance(Locale.GERMAN).parse(x[1]), when (x[0]) {
            "\$U" -> Monetary.getCurrency("UYU")
            "U\$S" -> Monetary.getCurrency("USD")
            else -> throw MonetaryException("Invalid currency code")
        }
    )
}
