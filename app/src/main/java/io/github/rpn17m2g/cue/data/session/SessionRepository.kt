package io.github.rpn17m2g.cue.data.session

import io.github.rpn17m2g.cue.data.input.ResolvedInput
import io.github.rpn17m2g.cue.processing.ProcessingJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class InputSession(
    val id: String,
    val input: ResolvedInput,
)

/**
 * In-memory hand-off between screens. Navigation carries only the input id; the
 * resolved paths and the last job stay here so routes never expose file paths.
 */
class SessionRepository {

    private val _current = MutableStateFlow<InputSession?>(null)
    val current: StateFlow<InputSession?> = _current.asStateFlow()

    var lastJob: ProcessingJob? = null
        private set

    fun create(input: ResolvedInput): InputSession {
        val session = InputSession(input.id, input)
        _current.value = session
        return session
    }

    fun find(inputId: String): InputSession? = _current.value?.takeIf { it.id == inputId }

    fun remember(job: ProcessingJob) {
        lastJob = job
    }

    fun clear() {
        _current.value = null
        lastJob = null
    }
}
