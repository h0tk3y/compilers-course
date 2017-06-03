package com.github.h0tk3y.compilersCourse.parsing

import me.sargunvohra.lib.cakeparse.api.and
import me.sargunvohra.lib.cakeparse.api.map
import me.sargunvohra.lib.cakeparse.api.optional
import me.sargunvohra.lib.cakeparse.api.zeroOrMore
import me.sargunvohra.lib.cakeparse.parser.BaseParser
import me.sargunvohra.lib.cakeparse.parser.Parser

class Separated<T, S>(val terms: List<T>,
                      private val separators: List<S>) {
    init {
        require(terms.size == separators.size + 1 || terms.isEmpty() && separators.isEmpty())
    }

    fun reduce(function: (T, S, T) -> T): T {
        var result = terms.first()
        for (i in separators.indices)
            result = function(result, separators[i], terms[i + 1])
        return result
    }

    fun reduceRight(function: (T, S, T) -> T): T {
        var result = terms.last()
        for (i in separators.indices.reversed())
            result = function(terms[i], separators[i], result)
        return result
    }
}

fun <T, S> leftAssociative(termParser: Parser<T>, separatorParser: Parser<S>, transform: (T, S, T) -> T): Parser<T> =
        separated(termParser, separatorParser).map { it.reduce(transform) }

fun <T, S> rightAssociative(termParser: Parser<T>, separatorParser: Parser<S>, transform: (T, S, T) -> T): Parser<T> =
        separated(termParser, separatorParser).map { it.reduceRight(transform) }

fun <T, S> separated(termParser: Parser<T>, separatorParser: Parser<S>, acceptZero: Boolean = false): BaseParser<Separated<T, S>> {
    val separatedParser = termParser
            .and(zeroOrMore(separatorParser and termParser))
            .map { (first, nexts) ->
                Separated(terms = listOf(first) + nexts.map { it.second }, separators = nexts.map { it.first })
            }
    return if (acceptZero)
        optional(separatedParser).map { it ?: Separated(terms = listOf(), separators = listOf()) } else
        separatedParser
}

fun <T, S> Parser<Separated<T, S>>.values() = map { it.terms }