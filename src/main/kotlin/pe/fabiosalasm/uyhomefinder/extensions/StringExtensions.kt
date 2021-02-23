package pe.fabiosalasm.uyhomefinder.extensions

import org.javamoney.moneta.Money
import java.text.NumberFormat
import java.util.*
import javax.money.Monetary

fun String.toMoney(): Money {
    require(this.split(" ").size == 2) { "Cannot parse string: $this" }
    val x = this.split(" ")
    val currency = Monetary.getCurrency(x[0]);
    return Money.of(NumberFormat.getInstance(Locale.GERMAN).parse(x[1]), currency);
}
