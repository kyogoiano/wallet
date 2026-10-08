package br.com.wallet.fraud.domain;

sealed public interface RecipientRisk {

    record Normal() implements RecipientRisk {}

    /**
     * Risk:
     * - takeover
     * - bot
     * - lavagem dispersa
     * - automation
     * @param recipientCount
     */
    record FanOut(int recipientCount) implements RecipientRisk {}

    /**
     * Risk (FanIn):
     * - conta laranja
     * - money collect
     * - mule
     * @param senderCount
     */
    record Mule(int senderCount) implements RecipientRisk {}

    /**
     * Risk (Combined/Fraud Ring):
     * - fraudulent network
     * - coordinated accounts
     * - triangularization
     * @param senderCount
     * @param recipientCount
     */
    record Ring(int senderCount, int recipientCount)
            implements RecipientRisk {}
}