/**
 * <b>Illustrative demo services.</b> The classes in this package are deliberately thin:
 * their methods return hardcoded strings rather than touching a database or an external
 * system. Their job is to make the AI wiring tangible — each {@code @EasyTool} method is a
 * real, callable tool the assistant/agent can invoke — not to be a reference order-management
 * backend.
 *
 * <p>In a production application these would be backed by persistence and would demarcate
 * their units of work (e.g. {@code @Transactional} on the CDI beans, or container-managed
 * transactions on the EJB). We keep them as stubs so the demo stays about <em>the AI
 * integration</em>, which is the genuinely new part; the CRUD around it is intentionally
 * left as an outline.
 *
 * <p><b>On bean types:</b> most services are idiomatic CDI ({@code @ApplicationScoped};
 * {@code CartService} is {@code @SessionScoped} because it holds per-user state).
 * {@code OrderService} is kept as an EJB ({@code @Stateless}) on purpose — see its class
 * javadoc — to show that EasyAI exposes {@code @EasyTool} methods on EJBs too, so an
 * existing EJB codebase can adopt it without a rewrite.
 */
package services;
