// IGNORE_BACKEND: JS_IR
// EXPECTED_REACHABLE_NODES: 1112
package foo

fun box(): String {
    var boo = "OK"
    var foo = object {
        val bar = object {
            val baz = boo
        }
    }

    return foo.bar.baz
}

