package cdibeans;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.primefaces.PrimeFaces;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dyntabs.BaseDyntabCdiBean;
import dyntabs.ai.EasyAI;
import dyntabs.ai.activity.ActivityContext;
import dyntabs.ai.activity.ActivityStore;
import dyntabs.annotation.DynTab;
import dyntabs.scope.TabScoped;
import interfaces.OrdersAssistant;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import model.DemoOrder;
import services.OrderActivityService;
import services.OrderCatalog;
import services.OrderService;
import services.SuggestionAdvisor;

/**
 * Backing bean for the <b>"Orders — Ambient Memory"</b> demo tab, the showcase for TabForge's ambient
 * activity feature.
 *
 * <h2>What this tab demonstrates</h2>
 * <p>It closes the UI&rarr;AI loop end to end:</p>
 * <ol>
 *   <li><b>Navigation capture (zero code):</b> {@code @DynTab(trackActivity = true)} makes opening or
 *       re-selecting this tab record a {@code NAVIGATION} event automatically.</li>
 *   <li><b>Business-action capture (one annotation):</b> clicking a row calls
 *       {@link OrderActivityService#viewOrder(String)}, whose {@code @ActivityTracked} annotation records
 *       "viewed order ORD-00x".</li>
 *   <li><b>Context injection (one builder call):</b> the {@link OrdersAssistant} is built with
 *       {@link dyntabs.ai.AssistantBuilder#withActivityContext(ActivityContext) withActivityContext(...)}
 *       scoped to <em>this</em> tab and session, so the user's recent actions are folded into the prompt
 *       on every question.</li>
 * </ol>
 * <p>The payoff: the user opens an order, types "cancel this" with no id, and the assistant resolves
 * "this" to the order it saw them open and calls {@link OrderService#cancelOrder(String)}.</p>
 *
 * <p><b>Familiar analogy:</b> a desk with a CCTV log. The tab is the desk, {@code trackActivity} and
 * {@code @ActivityTracked} are the cameras quietly noting what you touch, and the assistant is an aide
 * who reviews the last few minutes of footage before answering — so "cancel that" just works.</p>
 *
 * <h2>Tab-id scoping (why it lines up)</h2>
 * <p>The library stamps every recorded event with {@code TabScopedContextHolder.getCurrentTabId()},
 * which equals {@link #getDynTabId()}. We therefore scope the {@link ActivityContext} with the same
 * {@code getDynTabId()}, guaranteeing the read-side sees exactly what the capture-side wrote for this
 * tab.</p>
 */
@Named
@TabScoped
@DynTab(name = "OrdersDynTab", uniqueIdentifier = "Orders", title = "Orders - Ambient Memory",
        includePage = "/WEB-INF/include/orders/orders.xhtml", closeable = true, trackActivity = true)
public class OrdersBean extends BaseDyntabCdiBean {

    private static final Logger log = LoggerFactory.getLogger(OrdersBean.class);
    private static final long serialVersionUID = 1L;

    /** The AI's tools: operations it may perform on orders (find, cancel). */
    @Inject
    private OrderService orderService;

    /** The UI-action capture point: records "user viewed order X" via {@code @ActivityTracked}. */
    @Inject
    private OrderActivityService orderActivityService;

    /** The shared, application-scoped ambient timeline — written by the recorder, read by our context. */
    @Inject
    private ActivityStore activityStore;

    /** The single source of truth for the demo's sample orders — shared with the proactive rule. */
    @Inject
    private OrderCatalog orderCatalog;

    /**
     * The proactive-suggestion engine. Opening a row (below) not only records an activity event but also
     * <em>pokes</em> this advisor, which decides — deterministically — whether the recent pattern of views
     * is worth an unprompted suggestion in the AI panel.
     */
    @Inject
    private SuggestionAdvisor suggestionAdvisor;

    /** The assistant proxy, built once on tab open and wired to read this tab's activity each call. */
    private OrdersAssistant assistant;

    private DemoOrder selectedOrder;
    private String orderDetail = "";

    private final List<String> chatLog = new ArrayList<>();
    private String userMessage = "";

    /**
     * Build the tab's state when it is opened.
     *
     * <p>Called by the framework through {@code callAccessPointMethod()} the first time this tab is
     * launched. Here we load the demo orders and assemble the {@link OrdersAssistant}, binding it to an
     * {@link ActivityContext} scoped to the current session and <em>this</em> tab so that every later
     * question is answered with the user's freshest activity in view.</p>
     *
     * @param parameters launch parameters passed by the tab manager (unused in this demo)
     */
    @Override
    protected void accessPointMethod(Map parameters) {
        super.accessPointMethod(parameters);

        // Scope the ambient context to this user's session and this exact tab. getDynTabId() is the same
        // id the recorder stamps onto events, so the read-side and write-side agree on "this tab".
        ActivityContext activityContext = ActivityContext.of(activityStore)
                .forSession(activityRecorder.currentSessionId())
                .forTab(getDynTabId())
                .build();

        // withTools  -> the model can actually find/cancel orders.
        // withActivityContext -> the model is told, on every call, what the user just did in this tab,
        //                        so it can resolve "this"/"that" order without an explicit id.
        this.assistant = EasyAI.assistant(OrdersAssistant.class)
                .withTools(orderService)
                .withActivityContext(activityContext)
                .build();

        log.debug("OrdersBean opened: tabId={}, session={}", getDynTabId(),
                activityRecorder.currentSessionId());
    }

    /**
     * Open one order from the table for review.
     *
     * <p>Called by the row's "Open" button. Delegates to {@link OrderActivityService#viewOrder(String)}
     * — which, being {@code @ActivityTracked}, records the "viewed order" event that later lets the
     * assistant resolve deictic references — and shows the returned summary in the detail panel.</p>
     *
     * @param order the order row the user clicked
     */
    public void openOrder(DemoOrder order) {
        this.selectedOrder = order;
        // Records the "viewed order" activity event (via @ActivityTracked) BEFORE we return, so the
        // advisor below sees it when it re-reads the recent timeline.
        this.orderDetail = orderActivityService.viewOrder(order.getId());

        // Proactive hook: let the advisor look at what the user has been viewing and, if a rule fires,
        // push an unprompted suggestion into the AI panel. The call is fire-and-forget and never throws
        // — a proactive nicety must never break the click that triggered it.
        suggestionAdvisor.onOrderViewed(activityRecorder.currentSessionId());
    }

    /**
     * Send the typed message to the assistant and append the exchange to the on-screen log.
     *
     * <p>Called by the chat's send button. The assistant re-reads this tab's recent activity before
     * answering, so a message like "cancel this" is resolved against the most recently viewed order.</p>
     */
    public void ask() {
        if (userMessage == null || userMessage.isBlank()) {
            return;
        }
        String question = userMessage.trim();
        chatLog.add("<b>You:</b> " + question);
        try {
            String answer = assistant.ask(question);
            chatLog.add("<b>AI:</b> " + answer);
            userMessage = "";
        } catch (Exception ex) {
            log.error("OrdersAssistant.ask() failed: {}", ex.getMessage(), ex);
            FacesMessage msg = new FacesMessage(FacesMessage.SEVERITY_ERROR,
                    EasyAI.extractErrorMessage(ex), ex.getMessage());
            PrimeFaces.current().dialog().showMessageDynamic(msg);
        }
    }

    // --- getters / setters ---------------------------------------------------

    /** @return the demo orders from the shared {@link OrderCatalog} (the table's data). */
    public List<DemoOrder> getOrders() {
        return orderCatalog.all();
    }

    public DemoOrder getSelectedOrder() {
        return selectedOrder;
    }

    public void setSelectedOrder(DemoOrder selectedOrder) {
        this.selectedOrder = selectedOrder;
    }

    public String getOrderDetail() {
        return orderDetail;
    }

    public List<String> getChatLog() {
        return chatLog;
    }

    public String getUserMessage() {
        return userMessage;
    }

    public void setUserMessage(String userMessage) {
        this.userMessage = userMessage;
    }
}
