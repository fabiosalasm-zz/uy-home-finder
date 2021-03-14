package pe.fabiosalasm.uyhomefinder.dto.json

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import org.javamoney.moneta.Money
import java.lang.IllegalArgumentException
import java.math.BigDecimal
import java.net.URL
import javax.money.Monetary

object MoneyAsObjectSerializer : KSerializer<Money> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Money") {
        element<String>("currency")
        element<Double>("amount")
    }

    override fun serialize(encoder: Encoder, value: Money) {
        val structure = encoder.beginStructure(descriptor)
        structure.encodeStringElement(descriptor, 0, value.currency.currencyCode)
        structure.encodeDoubleElement(descriptor, 1, value.numberStripped.toDouble())
        structure.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): Money {
        return decoder.decodeStructure(descriptor) {
            var currencyCode: String? = null
            var amount: Double? = null
            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    0 -> currencyCode = decodeStringElement(descriptor, 0)
                    1 -> amount = decodeDoubleElement(descriptor, 1)
                    CompositeDecoder.DECODE_DONE -> break
                    else -> error("Unexpected index: $index")
                }
            }

            require(currencyCode != null) { "'currency' attribute should be present" }
            require(amount != null && amount > 0.0) { "'amount' atttribute should be present and greater than ZERO" }

            Money.of(BigDecimal.valueOf(amount), Monetary.getCurrency(currencyCode))
        }
    }
}

object UrlAsStringSerializer: KSerializer<URL> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("URL", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: URL) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): URL {
        val string =  decoder.decodeString()
        try {
            return URL(string)
        } catch (e: Exception) {
            throw IllegalArgumentException("'link' attribute should be a valid URL")
        }
    }
}