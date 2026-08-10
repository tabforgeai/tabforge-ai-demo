package services;

import java.util.List;
import java.util.Optional;

import dyntabs.ai.activity.UserActivityEvent;

/**
 * A <b>deterministic detector</b> that looks at what the user has recently done and decides — in plain
 * Java, with no AI involved — whether there is a proactive suggestion worth offering.
 *
 * <h2>Why this is an interface (and why it is deterministic)</h2>
 * <p>This is the "brain" of proactivity, and the design's central discipline: <b>a rule, not a model,
 * decides IF and WHAT to suggest.</b> An LLM is never asked to scan the activity log and free-associate —
 * that would be slow, costly, noisy, and an injection surface. Instead each rule is ordinary, testable,
 * side-effect-free Java over the recent activity window. The model is used later, and only to phrase the
 * chosen suggestion into a friendly sentence (see {@link SuggestionAdvisor}).</p>
 *
 * <p>Making it an interface means "adding proactivity" is just "adding a bean": drop in another
 * {@code @ApplicationScoped} class that implements this, and the {@link SuggestionAdvisor} — which injects
 * every implementation — will consult it automatically. This mirrors how business tools are added: one
 * small, focused unit at a time.</p>
 *
 * <h2>Contract</h2>
 * <ul>
 *   <li><b>Pure &amp; cheap.</b> {@link #evaluate} runs synchronously on the thread that recorded the
 *       activity (e.g. a JSF click), so it must be fast and must not perform I/O or mutate state.</li>
 *   <li><b>Conservative.</b> Return {@link Optional#empty()} unless the pattern is clear. Silence is the
 *       right default; a good proactive feature is one that rarely speaks.</li>
 *   <li><b>Self-describing output.</b> When it does fire, it returns a {@link Suggestion} carrying a
 *       stable {@code actionId}, a cooldown {@code key}, the accept-prompt, and the phrasing brief — so
 *       the advisor stays domain-agnostic.</li>
 * </ul>
 *
 * <p><b>Familiar analogy:</b> a smoke detector. It watches passively, applies one fixed rule ("particles
 * over threshold"), and only then sets off the alarm. It does not "reason" about whether there might be a
 * fire — it measures, then trips. Cheap, predictable, and quiet almost all the time.</p>
 *
 * @see SameCustomerOrdersRule the demo's one concrete rule
 * @see SuggestionAdvisor the engine that runs rules, phrases hits, and pushes them
 */
public interface SuggestionRule {

    /**
     * Inspect the recent activity window and decide whether to suggest something.
     *
     * @param recent the session's recent {@link UserActivityEvent}s, oldest first (as returned by the
     *               activity store); never {@code null}
     * @return a {@link Suggestion} if this rule's pattern is present, otherwise {@link Optional#empty()}
     */
    Optional<Suggestion> evaluate(List<UserActivityEvent> recent);
}
