interface Expression {
    fun evaluate(state: (Variable) -> Int): Int
}

class Const(val value: Int) : Expression {
    override fun evaluate(state: (Variable) -> Int): Int = value
}

data class Variable(val name: String) : Expression {
    override fun evaluate(state: (Variable) -> Int): Int = state(this)
}

fun transform2(stateFunction: (Variable) -> Int, e1: Expression, e2: Expression, transform: (Int, Int) -> Int): Int {
    val v1 = e1.evaluate(stateFunction)
    val v2 = e2.evaluate(stateFunction)
    return transform(v1, v2)
}

class Plus(val e1: Expression, val e2: Expression) : Expression {
    override fun evaluate(state: (Variable) -> Int): Int = transform2(state, e1, e2, Int::plus)
}

class Minus(val e1: Expression, val e2: Expression) : Expression {
    override fun evaluate(state: (Variable) -> Int): Int = transform2(state, e1, e2, Int::minus)
}

class Times(val e1: Expression, val e2: Expression) : Expression {
    override fun evaluate(state: (Variable) -> Int): Int = transform2(state, e1, e2, Int::times)
}

class Div(val e1: Expression, val e2: Expression) : Expression {
    override fun evaluate(state: (Variable) -> Int): Int = transform2(state, e1, e2, Int::div)
}

class Rem(val e1: Expression, val e2: Expression) : Expression {
    override fun evaluate(state: (Variable) -> Int): Int = transform2(state, e1, e2, Int::rem)
}

class And(val e1: Expression, val e2: Expression) : Expression {
    override fun evaluate(state: (Variable) -> Int): Int = transform2(state, e1, e2, Int::and)
}

class Or(val e1: Expression, val e2: Expression) : Expression {
    override fun evaluate(state: (Variable) -> Int): Int = transform2(state, e1, e2, Int::or)
}

class Not(val e1: Expression) : Expression {
    override fun evaluate(state: (Variable) -> Int): Int = e1.evaluate(state).let { if (it == 1) 0 else 1 }
}

class Eq(val e1: Expression, val e2: Expression) : Expression {
    override fun evaluate(state: (Variable) -> Int): Int = transform2(state, e1, e2) { v1, v2 -> if (v1 == v2) 1 else 0 }
}

class Neq(val e1: Expression, val e2: Expression) : Expression {
    override fun evaluate(state: (Variable) -> Int): Int = transform2(state, e1, e2) { v1, v2 -> if (v1 != v2) 1 else 0 }
}

class Gt(val e1: Expression, val e2: Expression) : Expression {
    override fun evaluate(state: (Variable) -> Int): Int = transform2(state, e1, e2) { v1, v2 -> if (v1 > v2) 1 else 0 }
}

class Lt(val e1: Expression, val e2: Expression) : Expression {
    override fun evaluate(state: (Variable) -> Int): Int = transform2(state, e1, e2) { v1, v2 -> if (v1 < v2) 1 else 0 }
}