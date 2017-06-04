fun parseLog(output: String) =
        output.lines().filter { it.isNotBlank() }.flatMap { it.split(" ").let { it.dropLast(1).map { null } + it.last().toInt() } }