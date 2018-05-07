package com.github.h0tk3y.compilersCourse.parsing

import com.github.h0tk3y.betterParse.combinators.*
import com.github.h0tk3y.betterParse.grammar.Grammar
import com.github.h0tk3y.betterParse.grammar.parseToEnd
import com.github.h0tk3y.betterParse.grammar.parser
import com.github.h0tk3y.betterParse.parser.Parser
import com.github.h0tk3y.compilersCourse.language.*
import com.github.h0tk3y.compilersCourse.languageUtils.resolveCalls

object ProgramGrammar : Grammar<Program>() {

    private val LPAR by token("\\(")
    private val RPAR by token("\\)")

    private val LSQ by token("\\[")
    private val RSQ by token("]")

    private val LBRC by token("\\{")
    private val RBRC by token("}")

    private val PLUS by token("\\+")
    private val MINUS by token("-")
    private val DIV by token("/")
    private val MOD by token("%")
    private val TIMES by token("\\*")
    private val OR by token("!!")
    private val AND by token("&&")
    private val EQU by token("==")
    private val NEQ by token("!=")
    private val LEQ by token("<=")
    private val GEQ by token(">=")
    private val LT by token("<")
    private val GT by token(">")

    private val NOT by token("!")

    private val COMMA by token(",")
    private val SEMI by token(";")
    private val ASGN by token(":=|=")

    private val IF by token("if\\b")
    private val THEN by token("then\\b")
    private val ELIF by token("elif\\b")
    private val ELSE by token("else\\b")
    private val FI by token("fi\\b")

    private val WHILE by token("while\\b")
    private val FOR by token("for\\b")
    private val DO by token("do\\b")
    private val OD by token("od\\b")

    private val TRY by token("try\\b")
    private val CATCH by token("catch\\b")
    private val FINALLY by token("finally\\b")
    private val YRT by token("yrt\\b")

    private val THROW by token("throw\\b")

    private val SKIP by token("skip\\b")

    private val REPEAT by token("repeat\\b")
    private val UNTIL by token("until\\b")

    private val BEGIN by token("begin\\b")
    private val END by token("end\\b")

    private val FUN by token("fun\\b")
    private val RETURN by token("return\\b")

    private val TRUE by token("true\\b")
    private val FALSE by token("false\\b")

    private val NUMBER by token("\\d+")
    private val CHARLIT by token("'.'")
    private val STRINGLIT by token("\".*?\"")

    private val REF_ID by token("[A-Z]\\w*")
    private val ID by token("[a-z]\\w*")

    private val WS by token("\\s+", ignore = true)
    private val NEWLINE by token("[\r\n]+", ignore = true)

    private val signToKind = mapOf(
            OR to Or,
            AND to And,
            LT to Lt,
            GT to Gt,
            EQU to Eq,
            NEQ to Neq,
            LEQ to Leq,
            GEQ to Geq,
            PLUS to Plus,
            MINUS to Minus,
            TIMES to Times,
            DIV to Div,
            MOD to Rem)

    private val numConst by
    (optional(MINUS) map { if (it == null) 1 else -1 }) * NUMBER map { (s, it) -> Const(s * it.text.toInt()) }

    private val const by
    numConst or
            CHARLIT.map { Const(it.text[1].toInt()) } or
            (TRUE asJust Const(1)) or
            (FALSE asJust Const(0))

    private val intArrayLiteral by
    -LSQ * separatedTerms(parser(::expr), COMMA, acceptZero = true) * -RSQ map {
        ArrayLiteral(isBoxed = false, initializers = it)
    }

    private val boxedArrayLiteral by
    -LBRC * separatedTerms(parser(::expr), COMMA, acceptZero = true) * -RBRC map {
        ArrayLiteral(isBoxed = true, initializers = it)
    }

    private val funCall: Parser<FunctionCall> by
            (ID * -LPAR * separatedTerms(parser(this::expr), COMMA, acceptZero = true) * -RPAR).map { (name, args) ->
                FunctionCall(UnresolvedFunction(name.text, args.size), args)
            }

    private val variable by (ID or REF_ID) use { Variable(text) }

    private val stringLiteral by STRINGLIT use { StringLiteral(text.removeSurrounding("\"", "\"")) }

    private val notTerm by (-NOT * parser(this::term)) map { UnaryOperation(it, Not) }
    private val parenTerm by -LPAR * parser(this::expr) * -RPAR

    private val nonIndexedTerm: Parser<Expression> by
        const or funCall or notTerm or variable or parenTerm or stringLiteral or intArrayLiteral or boxedArrayLiteral

    private val indexedTerm: Parser<FunctionCall> by
        (nonIndexedTerm * oneOrMore(-LSQ * parser(::expr) * -RSQ))
            .map { (term, indices) ->
                indices.fold(term) { acc, it -> FunctionCall(Intrinsic.ARRGET, listOf(acc, it)) } as FunctionCall
            }

    private val term by
        indexedTerm or nonIndexedTerm

    private val multiplicationOperator by TIMES or DIV or MOD
    private val multiplicationOrTerm by leftAssociative(term, multiplicationOperator) { l, o, r ->
        BinaryOperation(l, r, signToKind[o.type]!!)
    }

    private val sumOperator by PLUS or MINUS
    private val math: Parser<Expression> = leftAssociative(multiplicationOrTerm, sumOperator) { l, o, r ->
        BinaryOperation(l, r, signToKind[o.type]!!)
    }

    private val comparisonOperator by EQU or NEQ or LT or GT or LEQ or GEQ
    private val comparisonOrMath: Parser<Expression> by (math * optional(comparisonOperator * math))
            .map { (left, tail) -> tail?.let { (op, r) -> BinaryOperation(left, r, signToKind[op.type]!!) } ?: left }

    private val andChain by leftAssociative(comparisonOrMath, AND) { l, _, r -> BinaryOperation(l, r, And) }
    private val orChain by leftAssociative(andChain, OR) { l, _, r -> BinaryOperation(l, r, Or) }
    private val expr: Parser<Expression> by orChain

    private val skipStatement: Parser<Skip> by SKIP.map { Skip }

    private val functionCallStatement: Parser<FunctionCallStatement> by funCall.map { FunctionCallStatement(it) }

    private val assignmentStatement: Parser<Assign> by
        (variable * -ASGN * expr).map { (v, e) -> Assign(v, e) }

    private val arrayPutStatement: Parser<FunctionCallStatement> by
        (indexedTerm * -ASGN * expr)
            .map { (call, value) ->
                FunctionCallStatement(FunctionCall(Intrinsic.ARRSET, call.argumentExpressions + listOf(value)))
            }

    private val ifStatement: Parser<If> by
            (-IF * expr * -THEN *
             parser { statementsChain } *
             zeroOrMore(-ELIF * expr * -THEN * parser { statementsChain }) *
             optional(-ELSE * parser { statementsChain }).map { it ?: Skip } *
             -FI
            ).map { (condExpr, thenBody, elifs, elseBody) ->
                val elses = elifs.foldRight(elseBody) { (elifC, elifB), el -> If(elifC, elifB, el) }
                If(condExpr, thenBody, elses)
            }

    private val tryStatement: Parser<Try> by
            (-TRY * parser { statementsChain } *
             zeroOrMore(-CATCH * -LPAR * ID * ID * -RPAR * parser { statementsChain }).use { map { (exType, exData, block) ->
                 CatchBranch(ExceptionType(exType.text), Variable(exData.text), block) }
             } *
             optional(-FINALLY * parser { statementsChain }) *
             -YRT
            ).map { (body, catch, finally) ->
                Try(body, catch, finally ?: Skip)
            }

    private val throwStatement: Parser<Throw> by
            -THROW * -LPAR * ID * parser { expr } * -RPAR map { (exType, dataExpr) -> Throw(ExceptionType(exType.text), dataExpr) }

    private val forStatement: Parser<Chain> by
            (-FOR * parser { statement } * -COMMA * parser { expr } * -COMMA * parser { statement } * -DO *
             parser { statementsChain } * -OD
            ).map { (init, condition, doAfter, body) ->
                Chain(init, While(condition, Chain(body, doAfter)))
            }

    private val whileStatement: Parser<While> by (-WHILE * expr * -DO * parser { statementsChain } * -OD)
            .map { (cond, body) -> While(cond, body) }

    private val repeatStatement: Parser<Chain> by (-REPEAT * parser { statementsChain } * -UNTIL * expr).map { (body, cond) ->
        Chain(body, While(UnaryOperation(cond, Not), body))
    }

    private val returnStatement: Parser<Return> by -RETURN * expr map { Return(it) }

    private val statement: Parser<Statement> by skipStatement or
            functionCallStatement or
            assignmentStatement or
            ifStatement or
            whileStatement or
            forStatement or
            repeatStatement or
            returnStatement or
            tryStatement or
            throwStatement or
            arrayPutStatement

    private val functionDeclaration: Parser<FunctionDeclaration> by
            (-FUN * ID * -LPAR * separatedTerms(ID, COMMA, acceptZero = true) * -RPAR * -BEGIN * parser { statementsChain } * -END)
                    .map { (name, paramNames, body) ->
                        FunctionDeclaration(name.text, paramNames.map { Variable(it.text) }, body)
                    }

    private val statementsChain: Parser<Statement> by
            separatedTerms(statement, optional(SEMI)) * -optional(SEMI) map { chainOf(*it.toTypedArray()) }

    override val rootParser: Parser<Program> by oneOrMore(functionDeclaration or (statement and optional(SEMI) use { t1 })).map {
        val functions = it.filterIsInstance<FunctionDeclaration>()
        val statements = it.filterIsInstance<Statement>().let { if (it.isEmpty()) listOf(Skip) else it }
        val rootFunc = FunctionDeclaration("main", listOf(), chainOf(*statements.toTypedArray()))
        Program(functions + rootFunc, rootFunc)
    }
}

internal fun readProgram(text: String): Program {
    val parsed = ProgramGrammar.parseToEnd(text)
    return resolveCalls(parsed)
}