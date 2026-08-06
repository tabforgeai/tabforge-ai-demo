package model;

/**
 * The typed shape of a customer's ordering intent, extracted once from free text at the start of
 * the {@code EasyAI.flow()} order pipeline and then carried — unchanged — through every
 * deterministic step that follows.
 *
 * <p>This little record is where the demo crosses from the fuzzy, natural-language world into the
 * plain-Java world. The first flow step calls {@code EasyAI.extract(OrderRequest.class).from(text)}
 * to turn a sentence like <em>"Order 2 laptops for user U123, apply loyalty credit, deliver to My
 * City. If out of stock, use warehouse WH-EU."</em> into a populated {@code OrderRequest}. From
 * that point on no step needs the LLM again — {@code checkStock}, {@code pay}, {@code createOrder},
 * {@code ship} all read strongly-typed fields, not prose.</p>
 *
 * <p><b>Familiar analogy:</b> the order form a call-centre agent fills in while listening to the
 * customer. Once the boxes are ticked, the warehouse, till, and courier all work from the form —
 * nobody replays the phone call. The form is this record; "filling it in from the call" is the one
 * LLM step.</p>
 *
 * @param product           the item being ordered (e.g. {@code "laptops"})
 * @param quantity          how many units
 * @param userCode          the customer's code/id (e.g. {@code "U123"}), used for loyalty credit
 * @param paymentMethod     how to pay (e.g. {@code "card"}); may be {@code null} if the text didn't say
 * @param address           where to deliver; may be {@code null} if the text didn't say
 * @param alternateWarehouse the fallback warehouse to reserve from if the main stock is low
 *                           (e.g. {@code "WH-EU"}); may be {@code null}
 */
public record OrderRequest(
        String product,
        int quantity,
        String userCode,
        String paymentMethod,
        String address,
        String alternateWarehouse) {
}
