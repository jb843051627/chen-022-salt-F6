package com.fc.v2.service.renew;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import com.fc.v2.model.auto.TSaltRenewSign;
import org.junit.jupiter.api.Test;

/**
 * {@link RenewRules} 纯规则单测：三档算齐办法、分歧挂起、省档先查旧证、唯一裁决去向。
 *
 * @author fuce
 * @date 2026-10-05
 */
class RenewRulesTest {

    private TSaltRenewSign stamp(String signer, int verdict) {
        TSaltRenewSign s = new TSaltRenewSign();
        s.setSigner(signer);
        s.setVerdict(verdict);
        s.setValid(RenewRules.SIGN_VALID);
        return s;
    }

    private List<TSaltRenewSign> stamps(TSaltRenewSign... items) {
        return new ArrayList<>(java.util.Arrays.asList(items));
    }

    @Test
    void needCountAndModeByNode() {
        assertEquals(1, RenewRules.needCount(RenewRules.NODE_COUNTY));
        assertEquals(2, RenewRules.needCount(RenewRules.NODE_CITY));
        assertEquals(1, RenewRules.needCount(RenewRules.NODE_PROVINCE));
        assertEquals(RenewRules.MODE_FIRST_ONE, RenewRules.modeOf(RenewRules.NODE_COUNTY));
        assertEquals(RenewRules.MODE_TWO_SAME, RenewRules.modeOf(RenewRules.NODE_CITY));
        assertEquals(RenewRules.MODE_CHECK_ONE, RenewRules.modeOf(RenewRules.NODE_PROVINCE));
        assertThrows(IllegalArgumentException.class, () -> RenewRules.needCount(3));
        assertThrows(IllegalArgumentException.class, () -> RenewRules.modeOf(-1));
    }

    @Test
    void countyFirstOnePasses() {
        // 头一名落地这一档便算过
        RenewRules.Decision waiting = RenewRules.decide(
                RenewRules.NODE_COUNTY, stamps(), false);
        assertEquals(RenewRules.Outcome.WAIT, waiting.getOutcome());

        RenewRules.Decision pass = RenewRules.decide(
                RenewRules.NODE_COUNTY, stamps(stamp("县员甲", RenewRules.VERDICT_RENEW)), false);
        assertEquals(RenewRules.Outcome.ADVANCE, pass.getOutcome());
    }

    @Test
    void countyDenyStopsWholeBill() {
        RenewRules.Decision deny = RenewRules.decide(
                RenewRules.NODE_COUNTY, stamps(stamp("县员甲", RenewRules.VERDICT_DENY)), false);
        assertEquals(RenewRules.Outcome.VETO, deny.getOutcome());
    }

    @Test
    void cityTwoMustAgreeBothRenewAdvances() {
        RenewRules.Decision none = RenewRules.decide(
                RenewRules.NODE_CITY, stamps(), false);
        assertEquals(RenewRules.Outcome.WAIT, none.getOutcome());

        RenewRules.Decision one = RenewRules.decide(
                RenewRules.NODE_CITY, stamps(stamp("市员甲", RenewRules.VERDICT_RENEW)), false);
        assertEquals(RenewRules.Outcome.WAIT, one.getOutcome());

        RenewRules.Decision two = RenewRules.decide(
                RenewRules.NODE_CITY,
                stamps(stamp("市员甲", RenewRules.VERDICT_RENEW),
                        stamp("市员乙", RenewRules.VERDICT_RENEW)),
                false);
        assertEquals(RenewRules.Outcome.ADVANCE, two.getOutcome());
    }

    @Test
    void cityBothDenyVetoes() {
        RenewRules.Decision two = RenewRules.decide(
                RenewRules.NODE_CITY,
                stamps(stamp("市员甲", RenewRules.VERDICT_DENY),
                        stamp("市员乙", RenewRules.VERDICT_DENY)),
                false);
        assertEquals(RenewRules.Outcome.VETO, two.getOutcome());
    }

    @Test
    void citySplitHangsForReviewNeitherAdvancesNorStops() {
        // 一肯一否便挂在本档待复议，既不往前推，也不就此止住——两种落印次序同判
        RenewRules.Decision renewFirst = RenewRules.decide(
                RenewRules.NODE_CITY,
                stamps(stamp("市员甲", RenewRules.VERDICT_RENEW),
                        stamp("市员乙", RenewRules.VERDICT_DENY)),
                false);
        assertEquals(RenewRules.Outcome.WAIT, renewFirst.getOutcome());

        RenewRules.Decision denyFirst = RenewRules.decide(
                RenewRules.NODE_CITY,
                stamps(stamp("市员甲", RenewRules.VERDICT_DENY),
                        stamp("市员乙", RenewRules.VERDICT_RENEW)),
                false);
        assertEquals(RenewRules.Outcome.WAIT, denyFirst.getOutcome());
    }

    @Test
    void provinceWithoutOldCertCheckCannotFinish() {
        RenewRules.Decision none = RenewRules.decide(
                RenewRules.NODE_PROVINCE, stamps(), false);
        assertEquals(RenewRules.Outcome.WAIT, none.getOutcome());

        // 印落下了，但旧证缴回那一笔没过：仍挂省核准，不许办结
        RenewRules.Decision stampedNotChecked = RenewRules.decide(
                RenewRules.NODE_PROVINCE,
                stamps(stamp("省员甲", RenewRules.VERDICT_RENEW)), false);
        assertEquals(RenewRules.Outcome.WAIT, stampedNotChecked.getOutcome());
    }

    @Test
    void provinceDenyVetoes() {
        RenewRules.Decision deny = RenewRules.decide(
                RenewRules.NODE_PROVINCE,
                stamps(stamp("省员甲", RenewRules.VERDICT_DENY)), false);
        assertEquals(RenewRules.Outcome.VETO, deny.getOutcome());
    }

    @Test
    void provinceCheckedRenewSealsBill() {
        // 看过旧证已缴回，一人判可延续：省局点齐，办结封印
        RenewRules.Decision done = RenewRules.decide(
                RenewRules.NODE_PROVINCE,
                stamps(stamp("省员甲", RenewRules.VERDICT_RENEW)), true);
        assertEquals(RenewRules.Outcome.DONE, done.getOutcome());
    }
}
