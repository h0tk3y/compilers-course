package com.github.h0tk3y.compilersCourse.parsing

import com.github.h0tk3y.compilersCourse.language.*
import com.github.h0tk3y.compilersCourse.languageUtils.resolveCalls
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

private val PLUS = token("PLUS", "\\+")
private val MINUS = token("MINUS", "\\-")
private val DIV = token("DIV", "/")
private val MOD = token("MOD", "%")
private val TIMES = token("TIMES", "\\*")
private val OR = token("OR", "!!")
private val AND = token("AND", "&&")
private val EQU = token("EQU", "==")
private val NEQ = token("NEQ", "!=")
private val LEQ = token("LEQ", "<=")
private val GEQ = token("GEQ", ">=")
private val LT = token("LT", "<")
private val GT = token("GT", ">")

private val NOT = token("NOT", "!")

private val COMMA = token("COMMA", ",")
private val SEMI = token("SEMI", ";")
private val ASGN = token("ASGN", ":=")

private val IF = token("IF", "if\\b")
private val THEN = token("THEN", "then\\b")
private val ELIF = token("ELIF", "elif\\b")
private val ELSE = token("ELSE", "else\\b")
private val FI = token("FI", "fi\\b")

private val WHILE = token("WHILE", "while\\b")
private val FOR = token("FOR", "for\\b")
private val DO = token("DO", "do\\b")
private val OD = token("OD", "od\\b")

private val SKIP = token("SKIP", "skip\\b")

private val REPEAT = token("REPEAT", "repeat\\b")
private val UNTIL = token("UNTIL", "until\\b")

private val BEGIN = token("BEGIN", "begin\\b")
private val END = token("END", "end\\b")

private val FUN = token("FUN", "fun\\b")
private val RETURN = token("RETURN", "return\\b")

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

private val skipStatement: Parser<Skip> = SKIP.map { Skip }

private val functionCallStatement: Parser<FunctionCallStatement> = funCall.map { FunctionCallStatement(it) }

private val assignmentStatement: Parser<Assign> = (variable before ASGN and expr).map { (v, e) -> Assign(v, e) }

private val ifStatement: Parser<If> =
        ((IF then expr) and
                (THEN then ref { statementsChain }) and
                zeroOrMore((ELIF then expr before THEN) and ref { statementsChain }) and
                optional(ELSE then ref { statementsChain }).map { it ?: Skip } before FI)
                .map { (p, elseBody) -> val (q, elifs) = p; val (condExpr, thenBody) = q;
                    val elses = elifs.foldRight(elseBody) { (elifC, elifB), el -> If(elifC, elifB, el) }
                    If(condExpr, thenBody, elses)
                }

private val forStatement: Parser<Chain> = ((FOR then ref { statement } before COMMA) and
        (ref { expr } before COMMA) and
        (ref { statement } before DO) and
        (ref { statementsChain } before OD)).map {
    val (header, body) = it
    val (doBefore, doAfter) = header
    val (init, condition) = doBefore
    Chain(init, While(condition, Chain(body, doAfter)))
}

private val whileStatement: Parser<While> = ((WHILE then expr) before DO and (ref { statementsChain } before OD))
        .map { (cond, body) -> While(cond, body) }

private val repeatStatement: Parser<Chain> = ((REPEAT then ref { statementsChain } before UNTIL) and expr).map { (body, cond) ->
    Chain(body, While(UnaryOperation(cond, Not), body))
}

private val returnStatement: Parser<Return> = (RETURN then expr).map { Return(it) }

private val statement: Parser<Statement> = skipStatement or
        functionCallStatement or
        assignmentStatement or
        ifStatement or
        whileStatement or
        forStatement or
        repeatStatement or
        returnStatement

private val functionDeclaration: Parser<FunctionDeclaration> =
        ((FUN then ID) and (LPAR then separated(ID, COMMA, acceptZero = true).values() before RPAR before BEGIN) and (ref { statementsChain } before END))
                .map { (l, body) ->
                    val (name, paramNames) = l
                    FunctionDeclaration(name.raw, paramNames.map { Variable(it.raw) }, body)
                }

private val statementsChain: Parser<Statement> = separated(statement, SEMI).map { chainOf(*it.terms.toTypedArray()) } before optional(SEMI)

private val programParser: Parser<Program> = oneOrMore(functionDeclaration or (statement before optional(SEMI))).map {
    val functions = it.filterIsInstance<FunctionDeclaration>()
    val statements = it.filterIsInstance<Statement>().let { if (it.isEmpty()) listOf(Skip) else it }
    val rootFunc = FunctionDeclaration("main", listOf(), chainOf(*statements.toTypedArray()))
    Program(functions + rootFunc, rootFunc)
}

internal fun readProgram(text: String): Program {
    val parsed = tokens.lexer().lex(text).parseToEnd(programParser).value
    return resolveCalls(parsed)
}

fun main(args: Array<String>) {
    val x0 = 0
    val x1 = 1
    val x2 = 2
    val x3 = 3
//    val expr = ((((((x0<=x0)<=x2-362)>=((454!=x2)!=(x2>4))&&(444+724!=(x3!=x0))==(83-x2<=784+635))>(((x1>=x2)==(370>720))*((x3>x2)-(x1<=869))!=((x2==x3)!=(346&&243)!!x0-x0<=154*430)))!=(((499&&143)-(x0>489))-((162!=252)==(x3<129))>=((405+x2)*(x0<=568)!=(414*x1!=(x1>613))))*(((x1>129)<=(561<x1)!!(34>275)==(813==557))<=((604!!x1)==(x1<475))+((x1==x0)<(554!=x1)))!!(((602!!x2)==(270>x3))*((x2<608)-x2*x1)!=((223!!65)<x2+x1)+((865<=x0)-(708<762)))-(((794!=856)>=(x2>856))-(107*x2-(458&&x2))&&(x1+x3>=531-x0)<((230<x0)>(x2!!617)))<((((402!=72)==x0-x3!!(585!!329)<(x3&&x1))<=((527&&426)>x3+x1)-((x1<=x2)<=(x0==105)))>=(((173!=843)*(117<=x0)<((734>x3)!=849-x2))==(((596<=870)<(x2<x0))<=((x0==x2)>(401<x1))))))!!((((((x3>x0)>=(409&&x2))>(x1<=13)+(299-x0))==(((366!=x3)<=(633!!x1))<((367==135)>=x0+334)))>(((x2&&x1)>=(154>721))*((569!!x1)>(x2<=47))>=(((x2<x2)>=573*x2)!=(465-x2<(85>=x3)))))<=((((837>=77)-(100<=886)>=(231==x3)+x1*x3)!=((705*x0&&334-x0)>=(x3==x2&&x2<444)))<((x0*68<=(x3==933))*((290&&890)==338-594)>((455==x1)+(523>=x3)>=(x2<x1)+(778-x0)))))==(((613+273)*(x0!!x0)+(630+983<926-889))*((935*629>(x2<x0))>(x2==748)-x3*557)-((x1-x1)*(585*x0)>(x0<493!!x0==x3))*(((778!!516)==(x2!=268))*(980-6&&478!=x1))<=(((137>=x3)==(449==x3))+((720>598)>x2+x2)>=((122!=x0)<(x3&&335))-(614>x2&&(852&&174)))*((931<=453&&950<x2)+((x3!!x0)-(247>=676))!!((x0!=917)<=(4!=x1))>(x3*924>(x2<x2)))))
    val f = """
skip;
x0 := read();
x1 := read();
x2 := read();
x3 := read();
y := ((((((x0<=x0)<=x2-362)>=((454!=x2)!=(x2>4))&&(444+724!=(x3!=x0))==(83-x2<=784+635))>(((x1>=x2)==(370>720))*((x3>x2)-(x1<=869))!=((x2==x3)!=(346&&243)!!x0-x0<=154*430)))!=(((499&&143)-(x0>489))-((162!=252)==(x3<129))>=((405+x2)*(x0<=568)!=(414*x1!=(x1>613))))*(((x1>129)<=(561<x1)!!(34>275)==(813==557))<=((604!!x1)==(x1<475))+((x1==x0)<(554!=x1)))!!(((602!!x2)==(270>x3))*((x2<608)-x2*x1)!=((223!!65)<x2+x1)+((865<=x0)-(708<762)))-(((794!=856)>=(x2>856))-(107*x2-(458&&x2))&&(x1+x3>=531-x0)<((230<x0)>(x2!!617)))<((((402!=72)==x0-x3!!(585!!329)<(x3&&x1))<=((527&&426)>x3+x1)-((x1<=x2)<=(x0==105)))>=(((173!=843)*(117<=x0)<((734>x3)!=849-x2))==(((596<=870)<(x2<x0))<=((x0==x2)>(401<x1))))))!!((((((x3>x0)>=(409&&x2))>(x1<=13)+(299-x0))==(((366!=x3)<=(633!!x1))<((367==135)>=x0+334)))>(((x2&&x1)>=(154>721))*((569!!x1)>(x2<=47))>=(((x2<x2)>=573*x2)!=(465-x2<(85>=x3)))))<=((((837>=77)-(100<=886)>=(231==x3)+x1*x3)!=((705*x0&&334-x0)>=(x3==x2&&x2<444)))<((x0*68<=(x3==933))*((290&&890)==338-594)>((455==x1)+(523>=x3)>=(x2<x1)+(778-x0)))))==(((613+273)*(x0!!x0)+(630+983<926-889))*((935*629>(x2<x0))>(x2==748)-x3*557)-((x1-x1)*(585*x0)>(x0<493!!x0==x3))*(((778!!516)==(x2!=268))*(980-6&&478!=x1))<=(((137>=x3)==(449==x3))+((720>598)>x2+x2)>=((122!=x0)<(x3&&335))-(614>x2&&(852&&174)))*((931<=453&&950<x2)+((x3!!x0)-(247>=676))!!((x0!=917)<=(4!=x1))>(x3*924>(x2<x2)))));
write (y)
"""
    val message = tokens.lexer().lex(f).parseToEnd(programParser).value
    println(message)
}