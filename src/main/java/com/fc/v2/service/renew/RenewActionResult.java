package com.fc.v2.service.renew;

import com.fc.v2.model.auto.TSaltRenewBill;

/**
 * 一记动作（落印/退回）之后，单据与唯一裁决处说法一并交回。
 *
 * @author fuce
 * @date 2026-10-05
 */
public class RenewActionResult {

    private final TSaltRenewBill bill;
    private final RenewRules.Outcome outcome;
    private final String reason;

    public RenewActionResult(TSaltRenewBill bill, RenewRules.Outcome outcome, String reason) {
        this.bill = bill;
        this.outcome = outcome;
        this.reason = reason;
    }

    public TSaltRenewBill getBill() {
        return bill;
    }

    public RenewRules.Outcome getOutcome() {
        return outcome;
    }

    public String getReason() {
        return reason;
    }
}
