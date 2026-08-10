package services;

import java.util.Objects;

/**
 * One <b>proactive suggestion</b> a rule has decided is worth offering the user — the small, immutable
 * "the app would like to say something" object that flows from a {@link SuggestionRule} through the
 * {@link SuggestionAdvisor} to the AI panel.
 *
 * <h2>What it carries (and what it deliberately does not)</h2>
 * <p>A rule produces the <em>decision and the facts</em>, never the wording. So this object holds:</p>
 * <ul>
 *   <li>{@link #actionId()} — a stable machine name for the kind of suggestion (e.g. {@code
 *       "summarize-customer"}), so the UI and any analytics can tell suggestions apart.</li>
 *   <li>{@link #key()} — the cooldown/mute key (e.g. {@code "summarize-customer:Acme Corp"}); two
 *       firings with the same key are "the same nag" and are rate-limited together.</li>
 *   <li>{@link #promptForAccept()} — the ready-to-run prompt injected into the chat if the user accepts.
 *       This is why accepting needs <em>no</em> new execution path: it becomes an ordinary chat turn for
 *       the existing tab-aware assistant.</li>
 *   <li>{@link #phrasingBrief()} — a compact, factual briefing handed to the LLM whose <em>only</em> job
 *       is to turn it into one friendly sentence. The model phrases; it never decides.</li>
 * </ul>
 *
 * <p>Notice the sentence the user will read is <b>not</b> here — it does not exist yet when the rule
 * fires. The advisor produces it asynchronously by phrasing {@link #phrasingBrief()}. Keeping the
 * decision (fast, deterministic) separate from the wording (slow, LLM) is the whole design.</p>
 *
 * <p><b>Familiar analogy:</b> a reporter's note handed to a copy-editor. The reporter (the rule) writes
 * the facts and files it under a slug ({@code actionId}); the copy-editor (the LLM) turns the note into a
 * headline. The slug and the facts are fixed; only the headline is written later.</p>
 *
 * @see SuggestionRule the deterministic detector that produces these
 * @see SuggestionAdvisor the engine that phrases and pushes them
 */
public final class Suggestion {

    private final String actionId;
    private final String key;
    private final String promptForAccept;
    private final String phrasingBrief;

    /**
     * @param actionId        stable machine name for this kind of suggestion (e.g. {@code "summarize-customer"})
     * @param key             cooldown/mute key grouping repeats of the same nag (e.g. {@code actionId + ":" + customer})
     * @param promptForAccept the chat prompt to run verbatim if the user accepts
     * @param phrasingBrief   the factual briefing the LLM turns into one short sentence (no wording decisions)
     */
    public Suggestion(String actionId, String key, String promptForAccept, String phrasingBrief) {
        this.actionId = Objects.requireNonNull(actionId, "actionId");
        this.key = Objects.requireNonNull(key, "key");
        this.promptForAccept = Objects.requireNonNull(promptForAccept, "promptForAccept");
        this.phrasingBrief = Objects.requireNonNull(phrasingBrief, "phrasingBrief");
    }

    /** @return the stable machine name for this kind of suggestion. */
    public String actionId() {
        return actionId;
    }

    /** @return the cooldown/mute key; repeats sharing this key are rate-limited and muted together. */
    public String key() {
        return key;
    }

    /** @return the ready-to-run chat prompt injected into the panel when the user accepts. */
    public String promptForAccept() {
        return promptForAccept;
    }

    /** @return the factual briefing the LLM phrases into the one sentence the user sees. */
    public String phrasingBrief() {
        return phrasingBrief;
    }

    @Override
    public String toString() {
        return "Suggestion[" + actionId + " key=" + key + "]";
    }
}
