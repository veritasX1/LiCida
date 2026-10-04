package io.github.veritasx1.licida

/** One applied filter (with its value, e.g. 4 shades). */
data class Step(val filter: Filter, val value: Int = filter.defaultValue) {
    val label get() = if (filter.takesValue) "${filter.label} ($value)" else filter.label
    fun encode() = "${filter.id}:$value"

    companion object {
        fun decode(text: String) = text.split(":").let { parts -> Filter.byId(parts[0])?.let { Step(it, parts.getOrNull(1)?.toIntOrNull() ?: it.defaultValue) } }
    }
}

/** The edit history (card 10, handbook p. 29): a numbered list; undo/redo move the position, a new filter
 *  after an undo replaces what came after (as one would want). Position 0 = the original. */
data class Edits(val steps: List<Step> = emptyList(), val position: Int = 0) {
    val active get() = steps.take(position)
    val canUndo get() = position > 0
    val canRedo get() = position < steps.size

    fun add(step: Step) = Edits(active + step, position + 1)
    /** Change the value of the last step (Tontrennung's shades) without a new history entry. */
    fun replaceLast(step: Step) = if (position == 0) add(step) else Edits(active.dropLast(1) + step, position)
    fun undo() = if (canUndo) copy(position = position - 1) else this
    fun redo() = if (canRedo) copy(position = position + 1) else this
    fun jump(to: Int) = copy(position = to.coerceIn(0, steps.size))
    /** "Reset to Original" = undo all (handbook p. 28); redo brings the steps back. */
    fun reset() = copy(position = 0)

    /** "Replay": a stored sequence applied on top of what you see (handbook p. 31). */
    fun replay(sequence: List<Step>) = sequence.fold(this) { edits, step -> edits.add(step) }

    fun encode() = steps.joinToString(";") { it.encode() } + "|" + position

    companion object {
        fun decode(text: String?): Edits {
            if (text.isNullOrBlank() || "|" !in text) return Edits()
            val (list, at) = text.split("|", limit = 2)
            val steps = list.split(";").filter { it.isNotBlank() }.mapNotNull(Step::decode)
            return Edits(steps, (at.toIntOrNull() ?: steps.size).coerceIn(0, steps.size))
        }

        /** Apply the active steps to the original, one after another (the filters stack). */
        fun render(original: Pixels, steps: List<Step>): Pixels {
            var image = original
            var previous: Filter? = null
            for (step in steps) {
                image = Filters.apply(image, step.filter, step.value, previous)
                // Threshold twice inverts once; a third time is a fresh threshold again.
                previous = if (step.filter == Filter.Threshold && previous == Filter.Threshold) null else step.filter
            }
            return image
        }
    }
}

/** The three custom filter slots (handbook p. 30–31): a name and a sequence each; always three. */
data class Slot(val name: String, val steps: List<Step>) {
    fun encode() = name.replace("|", " ").replace(";", " ") + "|" + steps.joinToString(";") { it.encode() }

    companion object {
        fun decode(text: String?, index: Int): Slot {
            if (text.isNullOrBlank() || "|" !in text) return Slot("Platz ${index + 1}", emptyList())
            val (name, list) = text.split("|", limit = 2)
            return Slot(name.ifBlank { "Platz ${index + 1}" }, list.split(";").filter { it.isNotBlank() }.mapNotNull(Step::decode))
        }
    }
}
