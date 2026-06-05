package cdibeans;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import org.primefaces.PrimeFaces;
import org.primefaces.event.FileUploadEvent;
import org.primefaces.event.FilesUploadEvent;
import org.primefaces.model.file.UploadedFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import api.AgentEvent;
import api.AiEventChannel;
import api.EasyAiActivityBridge;
import dyntabs.BaseDyntabCdiBean;
import dyntabs.ai.Conversation;
import dyntabs.ai.EasyAI;
import dyntabs.ai.EasyAgent;
import dyntabs.ai.rag.DocumentSource;
import dyntabs.annotation.DynTab;
import dyntabs.scope.TabScoped;
import interfaces.CodeReviewer;
import interfaces.PolicyBot;
import interfaces.SalesBot;
import interfaces.SupportBot;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import services.CartService;
import services.InventoryService;
import services.OrderService;
import services.PaymentService;
import services.PricingService;
import services.ShippingService;
import services.UserService;

@Named
@TabScoped
@DynTab(name = "EasyAIDemoDynTab", uniqueIdentifier = "EasyAIDemo", title = "Easy AI Demo", includePage = "/WEB-INF/include/ai_demo/ai_demo.xhtml", closeable = true)

public class AiBean extends BaseDyntabCdiBean {
	private static final Logger log = LoggerFactory.getLogger(AiBean.class);

	Conversation chat = null;
	CodeReviewer reviewer = null;

	@Inject
	OrderService orderService;
	@Inject
	UserService userService;
	@Inject
	CartService cartService;

	@Inject
	InventoryService inventoryService;
	@Inject
	PaymentService paymentService;
	@Inject
	ShippingService shippingService;

	/** Application-scoped SSE registry; the bridge pushes Activity-Panel events through it. */
	@Inject
	AiEventChannel activityChannel;

	/** HTTP session id of the user who opened this tab — the SSE target for live events. */
	String sessionId;

	/** Translates this tab's EasyAI events into Activity-Panel rows for {@link #sessionId}. */
	EasyAiActivityBridge activityBridge;

	EasyAgent agent;

	@Override
	protected void accessPointMethod(Map parameters) {
		super.accessPointMethod(parameters);
		// groq:
		/*
		 * EasyAI.configure(EasyAIConfig.builder().provider("openai")
		 * .apiKey("YOUR_API_KEY_HERE").modelName("llama-3.3-70b-versatile")
		 * .baseUrl("https://api.groq.com/openai/v1/").build());
		 */

		// GPT-40-mini:
		/*
		 * EasyAI.configure(EasyAIConfig.builder().provider("openai").apiKey(
		 * "YOUR_API_KEY_HERE") .modelName("gpt-4o-mini").build());
		 */

		// Live observability bridge: capture this user's session id once, then stream every
		// EasyAI event from this tab into the pf-modern-template Activity Panel over SSE.
		this.sessionId = currentSessionId();
		this.activityBridge = new EasyAiActivityBridge(activityChannel, sessionId);

		// from easyai.properties:
		chat = EasyAI.chat().withMemory(20) // remember last 20 messages
				.withSystemMessage("You are a helpful tutor") // set AI personality
				.withEventListener(activityBridge) // narrate the turn into the Activity Panel
				.build();

		reviewer = EasyAI.assistant(CodeReviewer.class).build();

		// EastAgent:

		agent = EasyAI.agent().withServices(inventoryService, paymentService, orderService, shippingService)
				.withMaxSteps(10).withPlanningPrompt(true)
				.withStepListener(step -> log.info("[AGENT] Step {}: {}({}) -> {}", step.stepNumber(), step.toolName(),
						step.arguments(), step.result()))
				.withEventListener(activityBridge) // stream each tool call live to the Activity Panel
				.build();

		// pass any number of services:
		// bot = EasyAI.assistant(SupportBot.class).withTools(orderService, userService,
		// cartService).build();

	}

	/**
	 * Resolve the current HTTP session id from the active JSF request.
	 *
	 * <p>Both the JSF postbacks that drive this tab and the browser's SSE {@code EventSource}
	 * share one HTTP session (same {@code JSESSIONID} cookie), so this id is exactly the key the
	 * {@link AiEventChannel} uses to deliver events back to the same browser.</p>
	 *
	 * @return the session id, creating the session if necessary
	 */
	private String currentSessionId() {
		Object req = FacesContext.getCurrentInstance().getExternalContext().getRequest();
		return ((jakarta.servlet.http.HttpServletRequest) req).getSession(true).getId();
	}

	private List<String> messages = new ArrayList<>();

	public List<String> getMessages() {
		return messages;
	}

	private String userMessage = "Ask the question...";

	public String getUserMessage() {
		return userMessage;
	}

	public void setUserMessage(String userMessage) {
		this.userMessage = userMessage;
	}

	/**
	 * WARNING: you need to add a Groq root-CA certificate to the App server
	 * truststore, before trying this! Get certificate via Groq API:
	 * 
	 * keytool -printcert -rfc -sslserver api.groq.com:443 > c:\temp\groq.pem -in my
	 * case:
	 * c:\Eclipse_2025\plugins\org.eclipse.justj.openjdk.hotspot.jre.full.win32.x86_64_21.0.9.v20251105-0741\jre\bin\keytool
	 * -printcert -rfc -sslserver api.groq.com:443 > c:\temp\groq.pem - then split
	 * it into 3 .pem files (BEGIN-END) -then ad only 2. and 3.:
	 * c:\Eclipse_2025\plugins\org.eclipse.justj.openjdk.hotspot.jre.full.win32.x86_64_21.0.9.v20251105-0741\jre\bin\keytool
	 * -import -trustcacerts -alias groq_cert2 -file C:\temp\groq2.pem -keystore
	 * "C:\GlassFish_8\glassfish\domains\domain1\config\cacerts.p12" -storetype
	 * PKCS12 -storepass changeit
	 * c:\Eclipse_2025\plugins\org.eclipse.justj.openjdk.hotspot.jre.full.win32.x86_64_21.0.9.v20251105-0741\jre\bin\keytool
	 * -import -trustcacerts -alias groq_cert3 -file C:\temp\groq3.pem -keystore
	 * "C:\GlassFish_8\glassfish\domains\domain1\config\cacerts.p12" -storetype
	 * PKCS12 -storepass changeit
	 * 
	 * -then restart app server
	 * 
	 */
	public void sendMessage() {
		log.debug("Truststore: " + System.getProperty("javax.net.ssl.trustStore"));
		// C:\GlassFish_8\glassfish\domains\domain1/config/cacerts.p12
		if (userMessage != null && !userMessage.trim().isEmpty()) {
			messages.add("<b>Ti:</b> " + userMessage);
			try {
				String answer = chat.send(userMessage);
				messages.add("<b>AI:</b> " + answer);
				userMessage = "";
			} catch (Exception ex) {
				ex.printStackTrace();
				String mainMsg = EasyAI.extractErrorMessage(ex);// ex.getMessage();
				String detailMsg = mainMsg;
				if (ex instanceof RuntimeException && ex.getCause() != null
						&& ex.getCause() instanceof javax.net.ssl.SSLHandshakeException) {
					mainMsg = "Add Groq certificate to the app server trust store!";
				}
				FacesMessage message = new FacesMessage(FacesMessage.SEVERITY_ERROR, mainMsg, detailMsg);
				PrimeFaces.current().dialog().showMessageDynamic(message);
			}
		}
	}// of method

	private String reviewFeedback = "";

	public String getReviewFeedback() {
		return reviewFeedback;
	}

	public void setReviewFeedback(String reviewFeedback) {
		this.reviewFeedback = reviewFeedback;
	}

	public void handleFileUpload(FileUploadEvent event) {
		UploadedFile file = event.getFile();
		String theCode = getFileContent(file);
		setReviewFeedback(reviewer.review(theCode));
	}

	private String getFileContent(UploadedFile file) {
		StringBuilder text = new StringBuilder();
		try (Scanner scanner = new Scanner(file.getInputStream(), "UTF-8")) {
			while (scanner.hasNextLine()) {
				text.append(scanner.nextLine()).append("\n");
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return text.toString();
	}

	private String supportRequestMsg = "Show me all orders for john@example.com";

	public String getSupportRequestMsg() {
		return supportRequestMsg;
	}

	public void setSupportRequestMsg(String supportRequestMsg) {
		this.supportRequestMsg = supportRequestMsg;
	}

	private String supportFeedback = "";

	public String getSupportFeedback() {
		return supportFeedback;
	}

	public void setSupportFeedback(String supportFeedback) {
		this.supportFeedback = supportFeedback;
	}

	@Inject
	SupportBot bot;

	public void askSupport() {
		log.debug("askSupport call()");
		String supportRequestMsg = getSupportRequestMsg();
		if (supportRequestMsg != null && !supportRequestMsg.trim().isEmpty()) {
			try {
				setSupportFeedback(bot.ask(supportRequestMsg));
			} catch (Exception ex) {
				log.error("***askSupport() error: {} ", ex.getMessage());
				ex.printStackTrace();
				FacesMessage message = new FacesMessage(FacesMessage.SEVERITY_ERROR, EasyAI.extractErrorMessage(ex),
						ex.getMessage());
				PrimeFaces.current().dialog().showMessageDynamic(message);
			}
		}
	}

	List<DocumentSource> docs = new ArrayList<>();
	PolicyBot pBot = null;

	public void handleRAGFileUpload(FilesUploadEvent event) {
		log.debug("handleRAGFileUpload call()");
		docs.clear();
		List<UploadedFile> fileList = event.getFiles().getFiles();
		for (UploadedFile file : fileList) {
			docs.add(DocumentSource.of(file.getFileName(), file.getContent()));
		}
		pBot = EasyAI.assistant(PolicyBot.class).withRAG(docs, 3, 0.5).build();
		log.debug("pBot = {}", pBot);
	}

	private String ragRequestMsg = "Ask the question related to uploaded ducument";

	public String getRagRequestMsg() {
		return ragRequestMsg;
	}

	public void setRagRequestMsg(String ragRequestMsg) {
		this.ragRequestMsg = ragRequestMsg;
	}

	private String ragFeedback = "";

	public String getRagFeedback() {
		return ragFeedback;
	}

	public void setRagFeedback(String ragFeedback) {
		this.ragFeedback = ragFeedback;
	}

	public void askPolicy() {
		if (pBot == null) {
			FacesMessage message = new FacesMessage(FacesMessage.SEVERITY_ERROR, "Upload some documents first!",
					"Upload some documents first, then ask the question!");
			PrimeFaces.current().dialog().showMessageDynamic(message);
			return;
		}

		try {
			String answ = pBot.ask(getRagRequestMsg());
			log.debug("answ = " + answ);
			setRagFeedback(answ);
		} catch (Exception ex) {
			log.error("***askPolicy() error: {} ", ex.getMessage());
			ex.printStackTrace();
			FacesMessage message = new FacesMessage(FacesMessage.SEVERITY_ERROR, EasyAI.extractErrorMessage(ex),
					ex.getMessage());
			PrimeFaces.current().dialog().showMessageDynamic(message);
		}
	}

	// ----Combined - Tools + RAG stuff:
	SalesBot salesBot = null;
	PricingService pricingService = new PricingService(); // plain Java class as AI tool, without ANY annotations!

	public void handleCombinedRAGFileUpload(FileUploadEvent event) {
		String fileName = event.getFile().getFileName();
		byte[] fileBytes = event.getFile().getContent();
		salesBot = EasyAI.assistant(SalesBot.class).withRAG(DocumentSource.of(fileName, fileBytes))
				.withTools(pricingService).build();
	}

	private String combinedRagRequestMsg = "Ask the question related to uploaded ducument. Combine it with: what is the cost of product Laptor XYZ?";

	public String getCombinedRagRequestMsg() {
		return combinedRagRequestMsg;
	}

	public void setCombinedRagRequestMsg(String combinedRagRequestMsg) {
		this.combinedRagRequestMsg = combinedRagRequestMsg;
	}

	private String combinedRagFeedback = "";

	public String getCombinedRagFeedback() {
		return combinedRagFeedback;
	}

	public void setCombinedRagFeedback(String combinedRagFeedback) {
		this.combinedRagFeedback = combinedRagFeedback;
	}

	public void askSalesBot() {
		if (salesBot == null) {
			FacesMessage message = new FacesMessage(FacesMessage.SEVERITY_ERROR, "Upload some document first!",
					"Upload some document first!");
			PrimeFaces.current().dialog().showMessageDynamic(message);
			return;
		}
		try {
			String answ = salesBot.ask(getCombinedRagRequestMsg());
			log.debug("answ = " + answ);
			setCombinedRagFeedback(answ);
		} catch (Exception ex) {
			log.error("***askSupport() greska: {} ", ex.getMessage());
			ex.printStackTrace();
			FacesMessage message = new FacesMessage(FacesMessage.SEVERITY_ERROR, EasyAI.extractErrorMessage(ex),
					ex.getMessage());
			PrimeFaces.current().dialog().showMessageDynamic(message);
		}
	}

	// ----EasyAgent demo:

	private List<String> agentMessages = new ArrayList<>();

	public List<String> getAgentMessages() {
		return agentMessages;
	}

	private String userAgentMessage = "Ask the question...(Order 2 laptops for user U123, apply loyalty credit, deliver to My City. If out of stock, use warehouse WH-EU.)";

	public String getUserAgentMessage() {
		return userAgentMessage;
	}

	public void setUserAgentMessage(String userMessage) {
		this.userAgentMessage = userMessage;
	}

	/**
	 * Response will be something like this:
	 * 
	 * 
	 * ▎ "I have successfully processed your order. 2 laptops were reserved from
	 * warehouse WH-EU (REF-4521). A loyalty credit was applied, and the remaining
	 * amount was charged to your card (PAY-73291). Order ORD-8812 has been created
	 * and delivery to Belgrade has been scheduled — estimated arrival in 2-3
	 * business days."
	 */
	public void sendUserAgentMessage() {
		String answer = "";
		try {
			answer = agent.execute(getUserAgentMessage());
			agentMessages.add("<b>AI:</b> " + answer);
			// The agent core narrates its steps but not the final prose answer — push it to
			// the Activity Panel's chat bubble so the timeline ends with the actual reply.
			activityChannel.emit(sessionId, AgentEvent.assistantMessage(answer));
			setUserAgentMessage("");
		} catch (Exception ex) {
			ex.printStackTrace();
			answer = "Agent error: " + EasyAI.extractErrorMessage(ex);

			String mainMsg = "Agent error: " + EasyAI.extractErrorMessage(ex);
			String detailMsg = mainMsg;
			if (ex instanceof RuntimeException && ex.getCause() != null
					&& ex.getCause() instanceof javax.net.ssl.SSLHandshakeException) {
				mainMsg = "Add AI provider certificate to the app server trust store!";
			}
			FacesMessage message = new FacesMessage(FacesMessage.SEVERITY_ERROR, mainMsg, detailMsg);
			PrimeFaces.current().dialog().showMessageDynamic(message);
		}
	}

}
