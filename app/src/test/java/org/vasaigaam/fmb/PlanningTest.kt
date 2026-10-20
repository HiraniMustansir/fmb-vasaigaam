package org.vasaigaam.fmb
import org.junit.Test
import org.junit.Assert.*
import org.vasaigaam.fmb.domain.Planning
import java.math.BigDecimal as D
class PlanningTest {
 @Test fun thaalPlanning() { assertEquals(184, Planning.people(23)); assertEquals(24, Planning.thaals(185)) }
 @Test fun bufferAppliedOnce() { assertEquals(0, D("20.24").compareTo(Planning.scale(D("10"),D("100"),D("184"),D("10")))) }
 @Test fun compatibleConversions() { assertEquals(0, D("1500").compareTo(Planning.convert(D("1.5"),"kg","g"))) }
 @Test(expected=IllegalArgumentException::class) fun rejectsDensityGuess() { Planning.convert(D.ONE,"kg","l") }
 @Test fun sharedDemand() { assertEquals(D("4"),Planning.shortage(D("8")+D("6"),D("12"),D("3"),D("1"))) }
 @Test fun noNegativeShortages() { assertEquals(D.ZERO,Planning.shortage(D.ONE,D.TEN,D.ZERO,D.ZERO)) }
 @Test(expected=IllegalArgumentException::class) fun invalidYield() { Planning.scale(D.ONE,D.ZERO,D.TEN,D.ZERO) }
}
