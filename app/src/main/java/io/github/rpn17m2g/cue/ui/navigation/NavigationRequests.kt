package io.github.rpn17m2g.cue.ui.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NavigationRequest(val route: String)

/**
 * Lets non-Compose entry points (notification taps, share intents) ask the nav
 * host to move. The host consumes a request once it has navigated.
 */
class NavigationRequests {

    private val _request = MutableStateFlow<NavigationRequest?>(null)
    val request: StateFlow<NavigationRequest?> = _request.asStateFlow()

    fun navigate(route: String) {
        _request.value = NavigationRequest(route)
    }

    fun consume() {
        _request.value = null
    }
}

data class ErrorReport(
    val reason: String,
    val details: String? = null,
)

class ErrorReporter {

    private val _report = MutableStateFlow<ErrorReport?>(null)
    val report: StateFlow<ErrorReport?> = _report.asStateFlow()

    fun report(reason: String, details: String? = null) {
        _report.value = ErrorReport(reason, details)
    }

    fun clear() {
        _report.value = null
    }
}
