package com.codemobile.exec

data class ExecutionResult(
    val output: String,
    val error: String?,
    val durationMs: Long
) {
    val succeeded: Boolean get() = error == null
}
