package com.fortysevendeg.hood

import arrow.core.Either
import arrow.core.Option
import arrow.core.left
import arrow.core.right
import arrow.data.extensions.list.foldable.nonEmpty
import com.fortysevendeg.hood.models.*

private fun List<Benchmark>.filterUsingRegex(
  maybeRegex: Option<Regex>,
  rFilter: ((Benchmark) -> (Boolean)) -> List<Benchmark>
): List<Benchmark> =
  maybeRegex.fold({ this }) { regex -> rFilter { bm: Benchmark -> regex.matches(bm.getKey()) } }

fun List<Benchmark>.filterBenchmark(includeRegex: Option<Regex>): List<Benchmark> =
  filterUsingRegex(includeRegex) { this.filter(it) }

fun List<Benchmark>.filterBenchmarkNot(excludeRegex: Option<Regex>): List<Benchmark> =
  filterUsingRegex(excludeRegex) { this.filterNot(it) }

fun List<Benchmark>.filterBenchmarkIE(
  includeRegex: Option<Regex>,
  excludeRegex: Option<Regex>
): List<Benchmark> = filterBenchmark(includeRegex).filterBenchmarkNot(excludeRegex)

fun List<BenchmarkComparison>.getWrongResults(): List<BenchmarkComparison> =
  this.filter { it.result::class == BenchmarkResult.FAILED::class }

fun List<BenchmarkComparison>.handleFailures(): Either<BenchmarkComparisonError, List<BenchmarkComparison>> {
  val failures = this.getWrongResults()
  return if (failures.nonEmpty())
    BenchmarkComparisonError(BadPerformanceBenchmarkError(failures)).left()
  else this.right()
}