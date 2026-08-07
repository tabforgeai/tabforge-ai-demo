package cdibeans;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import api.AiEventChannel;
import api.EasyAiActivityBridge;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dyntabs.ai.EasyAI;
import dyntabs.ai.activity.ActivityContext;
import dyntabs.ai.activity.ActivityStore;
import dyntabs.ai.activity.EntityRef;
import dyntabs.ai.activity.UserActivityEvent;
import interfaces.NavigatorAssistant;
import jakarta.enterprise.context.SessionScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import services.InventoryService;
import services.OrderService;
import services.PaymentService;
import services.ShippingService;

/**
 * Session-scoped holder for the global, ambient-aware {@link NavigatorAssistant} behind the template's
 * AI panel.
 *
 * <h2>What this bean is for</h2>
 * <p>It builds the assistant <b>once per HTTP session</b>, binding it to an {@link ActivityContext}
 * scoped to that session (and to no single tab), so the assistant can see the user's navigation across
 * every tracked tab. Because the assistant carries its own chat memory, keeping one instance per session
 * lets the conversation in the panel build up naturally across messages.</p>
 *
 * <p><b>Why the session id is passed in rather than discovered here:</b> the panel's messages arrive over
 * a plain REST call ({@code POST /api/ai/chat}), where there is no {@code FacesContext} to read the
 * session from. The {@code AiChatResource} already has the {@code HttpServletRequest}, so it reads the
 * session id there and hands it to {@link #ask(String, String)}. We scope the {@link ActivityContext}
 * with that exact id — the same id the library stamps onto recorded navigation events — so the read-side
 * and write-side agree on "this session".</p>
 *
 * <h2>Tab-aware tools (why the same panel behaves differently per tab)</h2>
 * <p>The one panel is <b>tab-aware</b>: which tools it offers the model depends on which tab the user is
 * currently on. On the Orders tab it exposes the order tools, so "cancel order ORD-002" works; on Home it
 * exposes none, so the same request is politely declined. The tools are chosen by {@link #toolsFor(String)}.</p>
 *
 * <p><b>How it learns the active tab over REST</b> (where there is no {@code FacesContext}, so the
 * {@code @ViewScoped} {@code DynTabManager}/{@code DynTabTracker} are out of reach): it reads the same
 * session-scoped ambient timeline the panel already uses. Every tab opened or selected with
 * {@code @DynTab(trackActivity = true)} records a {@code NAVIGATION} event carrying the tab's
 * {@code uniqueIdentifier}; the most recent one is "where the user is now" — see
 * {@link #activeTabType(String)}. No new plumbing, no client change: the activity feature already knows.</p>
 *
 * <p><b>Why a shared memory:</b> tools are fixed at {@code build()} time, so changing them when the tab
 * changes means rebuilding the assistant. To keep the panel's conversation alive across those rebuilds we
 * hold ONE {@link ChatMemory} and hand it to each rebuild via {@code withChatMemory(...)} — so switching
 * tabs re-scopes the tools without wiping the chat.</p>
 *
 * <p><b>Familiar analogy:</b> a personal concierge assigned to you for the duration of your visit. You
 * can walk up to the front desk (the global panel) at any time; it's the same concierge who has been
 * quietly noting which rooms you've entered, so "where am I?" gets an instant, correct answer.</p>
 *
 * <h2>Who calls it / where the result goes</h2>
 * <p>{@code api.AiChatResource} calls {@link #ask(String, String)}; the returned text is streamed into
 * the chat by {@code PFTemplate.aiPanel.streamAssistant(reply)}.</p>
 *
 * @see NavigatorAssistant
 * @see dyntabs.ai.activity.ActivityContext
 */
@Named("globalAiAssistantBean")
@SessionScoped
public class GlobalAiAssistantBean implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(GlobalAiAssistantBean.class);

    /** The shared, application-scoped ambient timeline — written by tab navigation, read by our context. */
    @Inject
    private ActivityStore activityStore;

    /** Business services exposed to the single panel as tools (only their {@code @EasyTool} methods). */
    @Inject
    private OrderService orderService;
    @Inject
    private InventoryService inventoryService;
    @Inject
    private PaymentService paymentService;
    @Inject
    private ShippingService shippingService;

    /** Application-scoped SSE registry; lets the panel narrate its tool calls into the Activity panel. */
    @Inject
    private AiEventChannel activityChannel;

    /** Built lazily, then rebuilt whenever the active tab type changes (its tool set changes with it). */
    private transient NavigatorAssistant assistant;

    /** The tab type ({@code uniqueIdentifier}) the cached {@link #assistant} was last built for. */
    private transient String assistantTabType;

    /**
     * The panel's ONE conversation, shared across rebuilds. Transient because a
     * {@link MessageWindowChatMemory} is not {@code Serializable}; if the session ever passivates it is
     * re-created empty on the next message (a rare, acceptable reset — unlike a per-tab-switch reset,
     * which this field's whole point is to avoid).
     */
    private transient ChatMemory sharedMemory;

    /**
     * Answer one panel message, building the assistant on first use.
     *
     * <p>Called by {@code AiChatResource} for every {@code POST /api/ai/chat}. On the first call it builds
     * the {@link NavigatorAssistant} bound to a session-wide {@link ActivityContext}; subsequent calls
     * reuse it. The assistant re-reads the activity timeline on each call, so the reply always reflects
     * the user's latest navigation.</p>
     *
     * @param sessionId   the HTTP session id (supplied by the REST resource), used to scope the activity
     *                    context to this user's timeline
     * @param userMessage the user's typed question
     * @return the assistant's reply text, shown in the AI panel
     */
    public String ask(String sessionId, String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return "";
        }
        return assistant(sessionId).ask(userMessage.trim());
    }

    /**
     * Return the session's assistant, (re)building it when the active tab type has changed.
     *
     * <p>The context stays session-wide (no {@code forTab}) so the panel remains the whole-app concierge
     * that can still answer "where am I?" across tabs; only the <b>tool set</b> is narrowed to the active
     * tab via {@link #toolsFor(String)}. The shared {@link ChatMemory} is handed to every rebuild so the
     * conversation is preserved when the tools change.</p>
     */
    private NavigatorAssistant assistant(String sessionId) {
        String tabType = activeTabType(sessionId);
        if (assistant == null || !Objects.equals(tabType, assistantTabType)) {
            if (sharedMemory == null) {
                sharedMemory = MessageWindowChatMemory.withMaxMessages(30);
            }
            ActivityContext activityContext = ActivityContext.of(activityStore)
                    .forSession(sessionId)   // session-wide: no forTab(...), so it spans every tab
                    .build();
            // Narrate the panel's tool calls into the SAME Activity panel the agent/flow tabs feed.
            EasyAiActivityBridge activityBridge = new EasyAiActivityBridge(activityChannel, sessionId);
            assistant = EasyAI.assistant(NavigatorAssistant.class)
                    // Tools conditioned by the active tab — least privilege per surface. Only @EasyTool
                    // methods on the chosen services are exposed.
                    .withTools(toolsFor(tabType))
                    .withActivityContext(activityContext)   // it knows "this" / "what am I looking at"
                    .withChatMemory(sharedMemory)            // one conversation, survives the rebuild
                    .withEventListener(activityBridge)       // tool_call → tool_result live in the panel
                    .build();
            assistantTabType = tabType;
            log.debug("(Re)built global panel assistant for tab '{}' ({} tool object(s)) session {}",
                    tabType, toolsFor(tabType).length, sessionId);
        }
        return assistant;
    }

    /**
     * The tools this panel exposes while the user is on the given tab type — the heart of tab-awareness.
     *
     * <p>Keyed by the tab's {@code @DynTab(uniqueIdentifier = ...)}. On the Orders tab the panel can act on
     * orders; on any other tab it exposes <b>no</b> tools, so the model can only answer/navigate and will
     * decline actions it has no tool for. Extend this map as more tabs gain relevant tools.</p>
     *
     * @param tabType the active tab's unique identifier (e.g. {@code "Orders"}, {@code "Home"}), or
     *                {@code null} if no navigation has been recorded yet
     * @return the service objects to expose as tools; an empty array means "answers/navigation only"
     */
    private Object[] toolsFor(String tabType) {
        if ("Orders".equals(tabType)) {
            return new Object[] { orderService, inventoryService, paymentService, shippingService };
        }
        return new Object[0];
    }

    /**
     * Resolve which tab the user is currently on, from the session-scoped ambient timeline.
     *
     * <p>REST calls have no {@code FacesContext}, so the {@code @ViewScoped} tab beans are unreachable
     * here. Instead we read the same activity the panel already consumes: the most recent
     * {@link UserActivityEvent.Type#NAVIGATION} event carries the tab's {@code uniqueIdentifier} as its
     * primary {@link EntityRef} (recorded by the library for every {@code trackActivity=true} tab). The
     * latest such event is "where the user is now".</p>
     *
     * @param sessionId the session whose timeline to read
     * @return the active tab's unique identifier, or {@code null} if no tab navigation is on record
     */
    private String activeTabType(String sessionId) {
        List<UserActivityEvent> recent = activityStore.recent(sessionId, null, 50);
        // recent() is oldest-first; scan from the end to find the newest NAVIGATION event.
        for (int i = recent.size() - 1; i >= 0; i--) {
            UserActivityEvent e = recent.get(i);
            if (e.type() == UserActivityEvent.Type.NAVIGATION) {
                EntityRef ref = e.primaryEntity();
                if (ref != null && "tab".equals(ref.type())) {
                    return ref.id();   // the tab's uniqueIdentifier, e.g. "Orders" / "Home"
                }
            }
        }
        return null;
    }
}
