package com.ringfence.silentscheduler.core.ringer

/**
 * What happens to the ringer when a silence window ends (spec section 6.5, "When it
 * ends"). Distinct from [SilenceStyle], which controls what "silenced" sounds like —
 * this controls what "un-silenced" means, because the phone may already have been
 * silent before the window started.
 */
enum class RevertPolicy {
    /** Put the ringer back to whatever it was captured as when the window started. */
    RESTORE,

    /** Always end with sound on, regardless of what the phone was set to before. */
    SOUND
}
