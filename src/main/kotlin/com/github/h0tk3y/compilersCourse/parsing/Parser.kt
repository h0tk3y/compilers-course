package com.github.h0tk3y.compilersCourse.parsing

import com.github.h0tk3y.compilersCourse.language.*
import me.sargunvohra.lib.cakeparse.api.*
import me.sargunvohra.lib.cakeparse.lexer.Token
import me.sargunvohra.lib.cakeparse.parser.ParsableToken
import me.sargunvohra.lib.cakeparse.parser.Parser
import org.intellij.lang.annotations.RegExp

//region tokens

val tokens = linkedSetOf<ParsableToken>()
private fun token(name: String, @RegExp pattern: String, ignore: Boolean = false) =
        me.sargunvohra.lib.cakeparse.api.token(name, pattern, ignore).also { tokens.add(it) }

private val LPAR = token("LPAR", "\\(")
private val RPAR = token("RPAR", "\\)")

private val LSQ = token("LSQ", "\\[")
private val RSQ = token("LSQ", "\\]")

private val NOT = token("NOT", "!")
private val PLUS = token("PLUS", "\\+")
private val MINUS = token("MINUS", "\\-")
private val DIV = token("DIV", "/")
private val MOD = token("MOD", "%")
private val TIMES = token("TIMES", "\\*")
private val OR = token("OR", "\\|")
private val AND = token("AND", "&")
private val EQU = token("EQU", "==")
private val NEQ = token("NEQ", "!=")
private val LT = token("LT", "<")
private val GT = token("GT", ">")
private val LEQ = token("LEQ", "<=")
private val GEQ = token("GEQ", ">=")

private val COMMA = token("COMMA", ",")
private val SEMI = token("SEMI", ";")
private val ASGN = token("ASGN", ":=")

private val IF = token("IF", "if")
private val THEN = token("THEN", "then")
private val ELSE = token("ELSE", "else")
private val FI = token("FI", "fi")

private val WHILE = token("WHILE", "while")
private val DO = token("DO", "do")
private val OD = token("OD", "od")

private val BEGIN = token("BEGIN", "begin")
private val END = token("END", "end")

private val FUN = token("FUN", "fun")
private val RETURN = token("RETURN", "return")

private val NUMBER = token("NUMBER", "\\d+")
private val ID = token("ID", "\\w+")

private val WS = token("WS", "\\s+", ignore = true)
private val NEWLINE = token("NEWLINE", "[\r\n]+", ignore = true)

private val signToKind = mapOf<Token, BinaryOperationKind>(
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

private val const = NUMBER.map { Const(it.raw.toInt()) }
private val funCall: Parser<FunctionCall> =
        (ID and (LPAR then separated(ref(::expr), COMMA, acceptZero = true).values() before RPAR))
                .map { (name, args) -> FunctionCall(UnresolvedFunction(name.raw, args.size), args) }

private val variable = ID.map { Variable(it.raw) }

private val notTerm = (NOT then ref(::term)).map { UnaryOperation(it, Not) }
private val parenTerm = LPAR then ref(::expr) before RPAR

private val term: Parser<Expression> = const or funCall or notTerm or variable or parenTerm

val multiplicationOperator = TIMES or DIV or MOD
val multiplicationOrTerm = leftAssociative(term, multiplicationOperator) { l, o, r ->
    BinaryOperation(l, r, signToKind[o.type]!!)
}

val sumOperator = PLUS or MINUS
val math: Parser<Expression> = leftAssociative(multiplicationOrTerm, sumOperator) { l, o, r ->
    BinaryOperation(l, r, signToKind[o.type]!!)
}

val comparisonOperator = EQU or NEQ or LT or GT or LEQ or GEQ
val comparisonOrMath: Parser<Expression> = (math and optional(comparisonOperator and math))
        .map { (left, tail) -> tail?.let { (op, r) -> BinaryOperation(left, r, signToKind[op.type]!!) } ?: left }

private val andChain = leftAssociative(comparisonOrMath, AND, { l, _, r -> BinaryOperation(l, r, And) })
private val orChain = leftAssociative(andChain, OR, { l, _, r -> BinaryOperation(l, r, Or) })

private val expr: Parser<Expression> = orChain

private val functionCallStatement: Parser<FunctionCallStatement> = funCall.map { FunctionCallStatement(it) }

private val assignmentStatement: Parser<Assign> = (variable before ASGN and expr).map { (v, e) -> Assign(v, e) }

private val ifStatement: Parser<If> = ((IF then expr) and (THEN then ref { statementsChain }) and (ELSE then ref { statementsChain }) before FI)
        .map { (p, elseBody) -> val (condExpr, thenBody) = p; If(condExpr, thenBody, elseBody) }

private val whileStatement: Parser<While> = ((WHILE then expr) before DO and (ref { statementsChain } before OD))
        .map { (cond, body) -> While(cond, body) }

private val statement: Parser<Statement> = functionCallStatement or assignmentStatement or ifStatement or whileStatement

private val functionDeclaration: Parser<FunctionDeclaration> =
        ((FUN then ID) and (LPAR then separated(ID, COMMA, acceptZero = true).values() before RPAR before BEGIN) and (ref { statementsChain } before END))
                .map { (l, body) ->
                    val (name, paramNames) = l
                    FunctionDeclaration(name.raw, paramNames.map { Variable(it.raw) }, body)
                }

private val statementsChain: Parser<Statement> = separated(statement, SEMI).map { chainOf(*it.terms.toTypedArray()) }

private val programParser: Parser<Program> = oneOrMore(functionDeclaration or (statement before optional(SEMI))).map {
    val functions = it.filterIsInstance<FunctionDeclaration>()
    val statements = it.filterIsInstance<Statement>()
    val rootFunc = FunctionDeclaration("main", listOf(), chainOf(*statements.toTypedArray()))
    Program(functions + rootFunc, rootFunc)
}

@Suppress("UNCHECKED_CAST")
fun resolveCalls(program: Program): Program {

    val namedFunctions = (program.functionDeclarations + Intrinsic.declarations)
            .groupBy { it.name }
            .mapValues { (_, v) -> v.associateBy { it.parameters.size } }

    fun resolve(name: String, nArgs: Int) = namedFunctions[name]?.let { it[nArgs] }
                                            ?: throw IllegalStateException("Unresolved function $name, $nArgs arguments.")

    fun <T : Expression> resolveCallsIn(expression: Expression): T = when (expression) {
        is Const -> expression
        is Variable -> expression
        is FunctionCall -> {
            val resolvedArgs = expression.argumentExpressions.map<Expression, Expression>(::resolveCallsIn)
            if (expression.functionDeclaration is UnresolvedFunction) {
                val name = expression.functionDeclaration.name
                val nArgs = expression.functionDeclaration.dimensions
                expression.copy(resolve(name, nArgs), resolvedArgs)
            } else {
                expression.copy(argumentExpressions = resolvedArgs)
            }
        }
        is UnaryOperation -> expression.copy(resolveCallsIn(expression.operand))
        is BinaryOperation -> expression.copy(left = resolveCallsIn(expression.left),
                                              right = resolveCallsIn(expression.right))
    } as T

    fun resolveCallsInStatement(s: Statement): Statement = when (s) {
        Skip -> Skip
        is Assign -> s.copy(expression = resolveCallsIn(s.expression))
        is If -> s.copy(resolveCallsIn(s.condition), resolveCallsInStatement(s.trueBranch), resolveCallsInStatement(s.falseBranch))
        is While -> s.copy(resolveCallsIn(s.condition), resolveCallsInStatement(s.body))
        is Chain -> s.copy(resolveCallsInStatement(s.leftPart), resolveCallsInStatement(s.rightPart))
        is Return -> s.copy(resolveCallsIn(s.expression))
        is FunctionCallStatement -> s.copy(resolveCallsIn(s.functionCall))
    }

    fun resolveCallsInFunction(f: FunctionDeclaration) = FunctionDeclaration(f.name, f.parameters, resolveCallsInStatement(f.body))

    val mainResolved = resolveCallsInFunction(program.mainFunction)
    return Program(program.functionDeclarations.filter { it !== program.mainFunction }.map { resolveCallsInFunction(it) } + mainResolved,
                   mainResolved)
}

internal fun readProgram(text: String): Program {
    val parsed = tokens.lexer().lex(text).parseToEnd(programParser).value
    return resolveCalls(parsed)
}