package ludwig.interpreter

import ludwig.model.LambdaNode
import ludwig.model.NamedNode
import ludwig.model.VariableNode
import org.pcollections.HashPMap

class Closure(private val locals: HashPMap<NamedNode, Any>, private val lambda: LambdaNode) : Callable {
    private var argCount: Int = 0

    init {
        for (i in 0 until lambda.size) {
            val node = lambda[i]
            if (node !is VariableNode) {
                argCount = i
                break
            }
        }
    }

    override fun tail(args: Array<Any?>): Any? {
        var env: HashPMap<NamedNode, Any> = locals
        var evaluator: Evaluator? = null
        var result: Any? = null

        for (i in 0..lambda.size - 1) {
            val node = lambda[i]
            if (node is VariableNode) {
                env = env.plus(node as NamedNode, args[i])
            } else {
                if (evaluator == null) {
                    evaluator = Evaluator(env)
                }
                result = evaluator.eval(node)
                if (result is Signal) {
                    break
                }
            }
        }

        return result
    }

    override fun argCount(): Int {
        return argCount
    }
}
