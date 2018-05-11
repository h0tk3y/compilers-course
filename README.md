# compilers-course
Practical part of the compilers course (2017)

[![Build Status](https://travis-ci.org/h0tk3y/compilers-course.svg?branch=master)](https://travis-ci.org/h0tk3y/compilers-course)

### Common tasks and tests

The common tasks are tested with JUnit `Parameterized` tests generated from 
[`anlun/comiler-tests`](https://github.com/anlun/compiler-tests), see [`FileTestCases.kt`](https://github.com/h0tk3y/compilers-course/blob/master/src/test/kotlin/FileTestCases.kt). There's also a pack of my own  tests in the same source set.

### Implementation details

To precisely track boxed and unboxed values, the x86 back-end stores each value along with its kind (which is either *scalar* or *reference*).
The value kind is also passed to and returned from functions (the intrinsics show this in their signatures) along with the value. A type is returned
from a function in the `ebx` register.

Memory management is done through reference counting. There's a memory stress test here: [`arrayMemoryStressTest`](https://github.com/h0tk3y/compilers-course/blob/master/src/test/kotlin/ArrayTestCases.kt#L129).

### Personal task – Java-like exceptions

Check the [ExceptionTestCases.kt](https://github.com/h0tk3y/compilers-course/blob/c2d29b5c43408bf8d46e88a17bce3e219b583dff/src/test/kotlin/ExceptionTestCases.kt). The following is supported: 

* `throw (E e)` statement where `E` is an identifier for an exception type, and `e` is the exception data

* `try <...> catch (E1 e1) <...> catch (E2 e2) <...> finally <...> yrt` statement with optional `catch` and `finally` blocks

The semantics are derived from Java, in particular:

* the `finally` block is always triggered when the control flow leaves the `try` block or its `catch` blocks (indcluding `return` statements);

* throwing an exception from a `try` or `catch` goes through the `finally` block as well;

* throwing an exception from a `finally` block replaces the currently uncaught exception with a new one (no suppressed exceptions for now);

The implementation tracks exit points within the scopes  and compiles them into if-else-if-like catch blocks. The exception type is returned
by writing to the caller's stack frame (the value is returned normally).
