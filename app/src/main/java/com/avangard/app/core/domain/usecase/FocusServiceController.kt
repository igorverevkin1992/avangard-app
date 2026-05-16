package com.avangard.app.core.domain.usecase

/**
 * Side-effect seam for the ongoing-notification companion service. Concrete
 * Android implementation lives in the sync layer; tests can swap a no-op fake
 * without dragging Context into the use-case constructor.
 */
interface FocusServiceController {
    fun start()
}
