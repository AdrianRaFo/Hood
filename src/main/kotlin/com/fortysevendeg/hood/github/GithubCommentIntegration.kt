package com.fortysevendeg.hood.github

import arrow.core.Option
import arrow.effects.IO
import arrow.syntax.collections.firstOption
import com.fasterxml.jackson.databind.JsonNode
import com.fortysevendeg.hood.BenchmarkComparison
import com.fortysevendeg.hood.GhStatusState
import com.fortysevendeg.hood.OutputFileFormat
import com.fortysevendeg.hood.Printer.prettyPrintResult
import com.fortysevendeg.hood.github.GithubCommon.buildRequest
import com.fortysevendeg.hood.github.GithubCommon.client
import com.fortysevendeg.hood.github.GithubCommon.raiseError
import org.http4k.core.Body
import org.http4k.core.Method
import org.http4k.core.Status
import org.http4k.core.with
import org.http4k.format.Jackson
import org.http4k.format.Jackson.json

object GithubCommentIntegration {

  private const val commentIntro: String = "***Hood benchmark comparison:***"

  private fun generateCommentBody(results: List<BenchmarkComparison>): JsonNode {
    val content = "$commentIntro\n${results.prettyPrintResult(OutputFileFormat.MD)}"

    return Jackson {
      obj("body" to string(content))
    }
  }

  fun getPreviousCommentId(info: GhInfo, pull: Int): IO<Option<Long>> {
    val request = buildRequest(
      Method.GET,
      info,
      "issues/$pull/comments"
    )

    return IO { client(request) }.map { Jackson.asA(it.bodyString(), Array<GhComment>::class) }
      .map { list ->
        list.filter { it.body.startsWith(commentIntro) }
          .firstOption().map(GhComment::id)
      }
  }

  fun createComment(info: GhInfo, pull: Int, results: List<BenchmarkComparison>): IO<Boolean> {

    val request = buildRequest(
      Method.POST,
      info,
      "issues/$pull/comments"
    ).with(Body.json().toLens() of generateCommentBody(results))

    return IO { client(request) }.map { it.status == Status.CREATED }
  }

  fun updateComment(info: GhInfo, id: Long, results: List<BenchmarkComparison>): IO<Boolean> {

    val request = buildRequest(
      Method.PATCH,
      info,
      "issues/comments/$id"
    ).with(Body.json().toLens() of generateCommentBody(results))

    return IO { client(request) }.map { it.status == Status.OK }
  }

  private fun setStatus(info: GhInfo, commitSha: String, status: GhStatus): IO<Unit> {

    val body = Jackson {
      obj(
        "state" to string(status.state.value),
        "description" to string(status.description),
        "context" to string(status.context)
      )
    }

    val request =
      buildRequest(
        Method.POST,
        info,
        "statuses/$commitSha"
      ).with(Body.json().toLens() of body)

    return IO { client(request) }.flatMap {
      if (it.status == Status.CREATED)
        IO.unit
      else
        raiseError("Error creating the status (${it.status})")
    }
  }

  fun setPendingStatus(info: GhInfo, commitSha: String): IO<Unit> = setStatus(
    info, commitSha,
    GhStatus(
      GhStatusState.Pending,
      "Comparing Benchmarks"
    )
  )

  fun setSuccessStatus(info: GhInfo, commitSha: String): IO<Unit> = setStatus(
    info, commitSha,
    GhStatus(
      GhStatusState.Succeed,
      "Benchmarks comparison passed"
    )
  )

  fun setFailedStatus(info: GhInfo, commitSha: String, comment: String): IO<Unit> =
    setStatus(
      info,
      commitSha,
      GhStatus(GhStatusState.Failed, "Benchmarks comparison failed: $comment")
    )

}
