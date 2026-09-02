package com.mubashir.jarvis.llm

/** What Jarvis can actually do right now, as opposed to what it was told to say. */
data class Abilities(
    /** Whether spoken commands may act on the phone at all. */
    val phoneControl: Boolean = true,
    val canCall: Boolean = false,
    val canMessage: Boolean = false,
    val canReadContacts: Boolean = false,
    val canSeeLocation: Boolean = false,
    val canReadCalendar: Boolean = false,
    val canReadNotifications: Boolean = false,
    val canSearchInternet: Boolean = false,
    val remembers: Boolean = false,
    /** A brain on a machine somewhere, rather than the model in this phone. */
    val brainIsRemote: Boolean = false,
)

/** The things Jarvis might be able to do, named so they can be compared exactly. */
enum class Ability {
    Torch, Battery, Timer, OpenApp, Call, Message,
    Location, Calendar, Notifications, Internet, Remember, PhoneControl,
}

/** One line of the prompt, and which ability it is about. */
data class AbilityLine(val ability: Ability, val text: String)

/**
 * Who Jarvis is, built from what is true rather than written down once.
 *
 * The prompt used to be a constant with the capability list typed into it. That
 * list was written when nothing was wired up, and by the time contacts, calls,
 * messages and memory all worked it was still telling the model it could not
 * see contacts and had no memory — so Jarvis denied, in its own words, things
 * it had just done. Every new ability made the constant more wrong, and nothing
 * about adding one reminded anybody to fix it.
 *
 * Pure, so the one failure that matters can be tested: the prompt must never
 * claim Jarvis cannot do something Jarvis can do.
 */
object Persona {

    fun systemPrompt(abilities: Abilities): String = buildString {
        appendLine(IDENTITY)
        appendLine()
        appendLine(VOICE)

        val can = canDo(abilities)
        if (can.isNotEmpty()) {
            appendLine()
            appendLine("What you can do on the phone, when asked directly:")
            can.forEach { appendLine("- ${it.text}") }
        }

        val cannot = cannotDo(abilities)
        if (cannot.isNotEmpty()) {
            appendLine()
            appendLine("What you cannot do. Say so plainly if asked, and never pretend otherwise:")
            cannot.forEach { appendLine("- ${it.text}") }
        }
        // Always true, and so not part of the state-driven lists above. Kept
        // inside the same block rather than after a blank line, which read as a
        // second list with no heading.
        appendLine("- See the screen, the camera, or his files.")
        appendLine("- Change other phone settings, or fix Wi-Fi, Bluetooth or connectivity.")
        appendLine()
        append("You can talk, explain, translate, summarise, do arithmetic, and help him think.")
    }

    internal fun canDo(a: Abilities): List<AbilityLine> = buildList {
        if (a.phoneControl) {
            add(Ability.Torch to "Turn the torch on and off.")
            add(Ability.Battery to "Say what the battery level is.")
            add(Ability.Timer to "Set a timer.")
            add(Ability.OpenApp to "Open an app by name.")
        }
        if (a.canCall && a.canReadContacts) {
            add(
                Ability.Call to "Call someone in his contacts. The name and number are " +
                    "shown on screen and he confirms before it dials.",
            )
        }
        if (a.canMessage && a.canReadContacts) {
            add(
                Ability.Message to "Send a message to someone in his contacts. Shown and " +
                    "confirmed the same way.",
            )
        }
        if (a.canSeeLocation) add(Ability.Location to "Say where he is.")
        if (a.canReadCalendar) add(Ability.Calendar to "Read today's schedule.")
        if (a.canReadNotifications) add(Ability.Notifications to "Read his notifications.")
        if (a.canSearchInternet) add(Ability.Internet to "Look things up online.")
        if (a.remembers) {
            add(
                Ability.Remember to "Remember things about him and bring them up later. He " +
                    "can say \"remember that ...\" or \"forget ...\" at any time.",
            )
        }
    }

    internal fun cannotDo(a: Abilities): List<AbilityLine> = buildList {
        if (!a.phoneControl) {
            add(Ability.PhoneControl to "Act on the phone at all. He has switched that off in settings.")
        }
        if (!(a.canCall && a.canReadContacts)) add(Ability.Call to "Place calls.")
        if (!(a.canMessage && a.canReadContacts)) add(Ability.Message to "Send messages.")
        if (!a.canSeeLocation) add(Ability.Location to "Check where he is.")
        if (!a.canReadCalendar) add(Ability.Calendar to "Read his calendar.")
        if (!a.canReadNotifications) add(Ability.Notifications to "Read his notifications.")
        if (!a.canSearchInternet) {
            add(
                Ability.Internet to
                    if (a.brainIsRemote) "Look anything up online. You have no internet access."
                    else "Look anything up online, check the weather, or read live " +
                        "information. You are offline.",
            )
        }
        if (!a.remembers) add(Ability.Remember to "Remember anything between conversations.")
    }

    /** Reads as a pair at the call site, stored as a line that knows what it is about. */
    private fun MutableList<AbilityLine>.add(pair: Pair<Ability, String>) {
        add(AbilityLine(pair.first, pair.second))
    }

    private const val IDENTITY =
        "You are Jarvis, Mubashir's personal assistant. You run on his own hardware and answer to nobody else."

    /**
     * The character, and the part he cares about most: "Tony Stark wala Jarvis
     * buk buk nahi karta." An assistant that explains itself before every
     * action, restates the question, and pads with courtesies is not being
     * polite — it is being slow. Brevity here is the personality, not a
     * setting.
     */
    private const val VOICE = """How you speak:
- Answer in English, even when he writes in Urdu, Hindi or Hinglish. You understand all of them.
- One sentence when one will do. Never two when one works.
- Do the thing, then say what happened in a few words. Never explain what you are about to do.
- Never repeat his question back to him, and never start with "Sure", "Certainly" or "I'd be happy to".
- If you do not know, say so in four words, not four sentences.
- Never say you are an AI or a language model. He knows.
- Plain text only. No markdown, no emoji, no bullet points, no headings."""
}
