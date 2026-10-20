package org.vasaigaam.fmb.domain
import java.math.BigDecimal
import java.math.RoundingMode
object Planning {
 fun people(thaals: Int, perThaal: Int = 8): Int { require(thaals >= 0 && perThaal > 0); return Math.multiplyExact(thaals, perThaal) }
 fun thaals(people: Int, perThaal: Int = 8): Int { require(people >= 0 && perThaal > 0); return BigDecimal(people).divide(BigDecimal(perThaal),0,RoundingMode.CEILING).intValueExact() }
 fun scale(quantity: BigDecimal, base: BigDecimal, target: BigDecimal, buffer: BigDecimal): BigDecimal {
  require(quantity.signum() >= 0 && base.signum() > 0 && target.signum() >= 0 && buffer in BigDecimal.ZERO..BigDecimal(100))
  return quantity.multiply(target).multiply(BigDecimal.ONE + buffer.movePointLeft(2)).divide(base, 6, RoundingMode.HALF_UP)
 }
 fun convert(quantity: BigDecimal, from: String, to: String): BigDecimal {
  val units = mapOf("kg" to ("mass" to BigDecimal(1000)), "g" to ("mass" to BigDecimal.ONE), "l" to ("volume" to BigDecimal(1000)), "ml" to ("volume" to BigDecimal.ONE), "each" to ("count" to BigDecimal.ONE))
  val a = requireNotNull(units[from]); val b = requireNotNull(units[to]); require(a.first == b.first) { "Incompatible units" }
  return quantity.multiply(a.second).divide(b.second,6,RoundingMode.HALF_UP)
 }
 fun shortage(required: BigDecimal, onHand: BigDecimal, reservedElsewhere: BigDecimal, incoming: BigDecimal) = (required - onHand + reservedElsewhere - incoming).max(BigDecimal.ZERO)
}
