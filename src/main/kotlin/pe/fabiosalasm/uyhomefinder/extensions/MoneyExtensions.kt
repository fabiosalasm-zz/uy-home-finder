package pe.fabiosalasm.uyhomefinder.extensions

import org.javamoney.moneta.Money

fun Money?.orElse(another: Money): Money = this ?: another