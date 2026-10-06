package com.fc.v2.service.renew;

import java.util.List;

import com.fc.v2.model.auto.TSaltRenewSign;

/**
 * 延续换证三级核签规则。
 *
 * <p>三档按次序走：0 县盐务管理处初核 / 1 市盐业主管处复审 / 2 省盐务管理局核准。
 * 每块四栏（应落几人、按什么算法算齐、如今落了几人、整单此刻停在第几块）中的
 * "应落几人"与"按什么算法算齐"在此一处定死，各档算法各异、互不套用。
 *
 * <p>这张单如今压在哪一关、下一关让不让走、两名审查员判得不同时停在何处、
 * 分歧时是就此打住还是挂起待复议、省局点齐之后封不封印——只认本类
 * {@link #decide} 给出的说法，别处不再作答。日后要换一种落法，另起一套接着，
 * 不要改动本类与查当前关的旧查询。
 *
 * @author fuce
 * @date 2026-10-05
 */
public final class RenewRules {

    private RenewRules() {
    }

    /** 档口：县盐务管理处初核 */
    public static final int NODE_COUNTY = 0;
    /** 档口：市盐业主管处复审 */
    public static final int NODE_CITY = 1;
    /** 档口：省盐务管理局核准 */
    public static final int NODE_PROVINCE = 2;
    /** 档口总数，越过最后一档无处可去 */
    public static final int NODE_TOTAL = 3;

    /** 核签情形：在核 */
    public static final int STATUS_RUNNING = 0;
    /** 核签情形：已办结（省局点齐，封印） */
    public static final int STATUS_DONE = 1;
    /** 核签情形：已否决（哪一档否了，全单就此止住） */
    public static final int STATUS_VETOED = 2;

    /** 判词：可延续 */
    public static final int VERDICT_RENEW = 1;
    /** 判词：不予延续 */
    public static final int VERDICT_DENY = 0;

    /** 算齐办法：一名即可，头一名落地这一档便算过（县初核） */
    public static final int MODE_FIRST_ONE = 0;
    /** 算齐办法：两名审查员且须同判（市复审） */
    public static final int MODE_TWO_SAME = 1;
    /** 算齐办法：一名，但先要看过旧证缴回那一笔（省核准） */
    public static final int MODE_CHECK_ONE = 2;

    /** 落印流水：有效 */
    public static final int SIGN_VALID = 0;
    /** 落印流水：本轮退回当场清零（旧笔留底，不算数） */
    public static final int SIGN_CLEARED = 1;

    /**
     * 该落几人——按档口给数。
     */
    public static int needCount(int node) {
        switch (node) {
            case NODE_COUNTY:
                return 1;
            case NODE_CITY:
                return 2;
            case NODE_PROVINCE:
                return 1;
            default:
                throw new IllegalArgumentException("无此档口: " + node);
        }
    }

    /**
     * 按什么算法算齐——按档口给办法。
     */
    public static int modeOf(int node) {
        switch (node) {
            case NODE_COUNTY:
                return MODE_FIRST_ONE;
            case NODE_CITY:
                return MODE_TWO_SAME;
            case NODE_PROVINCE:
                return MODE_CHECK_ONE;
            default:
                throw new IllegalArgumentException("无此档口: " + node);
        }
    }

    /** 本档裁决后的去向 */
    public enum Outcome {
        /** 点数未齐（含市里一肯一否）：挂在本档，不往前推，也不就此止住 */
        WAIT,
        /** 本档算齐且判可延续：只许推进紧邻的下一道 */
        ADVANCE,
        /** 省局点齐：办结封印，此后谁也添不进印 */
        DONE,
        /** 本档判否：全单止住，往后各档再落不得印 */
        VETO
    }

    /** 一处给出的裁决：去向 + 给人看的说法 */
    public static final class Decision {
        private final Outcome outcome;
        private final String reason;

        Decision(Outcome outcome, String reason) {
            this.outcome = outcome;
            this.reason = reason;
        }

        public Outcome getOutcome() {
            return outcome;
        }

        public String getReason() {
            return reason;
        }
    }

    /**
     * 落印之后本档怎么算——全单唯一的裁决处。
     *
     * @param node        单据此刻实际停在的档口（以库中单据为准，非递单带来的关号）
     * @param roundStamps 本轮本档逐笔点出的有效落印（顺落印次序）
     * @param checkPassed 省档专用：本轮是否已有"旧证已缴回"那一笔核查；他档给 false
     * @return 裁决：挂起 / 推进 / 办结 / 否决
     */
    public static Decision decide(int node, List<TSaltRenewSign> roundStamps, boolean checkPassed) {
        int done = roundStamps == null ? 0 : roundStamps.size();
        int need = needCount(node);

        if (node == NODE_COUNTY) {
            // 初核一人就够，头一名落地这一档便算过
            if (done < need) {
                return new Decision(Outcome.WAIT, "县初核尚未落印，挂在县初核");
            }
            int verdict = roundStamps.get(0).getVerdict();
            if (verdict == VERDICT_RENEW) {
                return new Decision(Outcome.ADVANCE, "县初核判可延续，放行进市复审");
            }
            return new Decision(Outcome.VETO, "县初核判不予延续，全单止住");
        }

        if (node == NODE_CITY) {
            // 复审两名审查员且须同判
            if (done < need) {
                return new Decision(Outcome.WAIT, "市复审应落两名，现落" + done + "名，挂在市复审");
            }
            int first = roundStamps.get(0).getVerdict();
            int second = roundStamps.get(1).getVerdict();
            if (first == second) {
                if (first == VERDICT_RENEW) {
                    return new Decision(Outcome.ADVANCE, "两名审查员同判可延续，放行进省核准");
                }
                return new Decision(Outcome.VETO, "两名审查员同判不予延续，全单止住");
            }
            // 一肯一否：挂在本档待复议，既不往前推，也不就此止住
            return new Decision(Outcome.WAIT, "两名审查员一肯一否，挂在市复审待复议");
        }

        if (node == NODE_PROVINCE) {
            // 核准一人，但先要看过往有无未缴回旧证那一笔，看过才落得下去
            if (done < need) {
                return new Decision(Outcome.WAIT, "省核准尚未落印，挂在省核准");
            }
            int verdict = roundStamps.get(0).getVerdict();
            if (verdict == VERDICT_DENY) {
                return new Decision(Outcome.VETO, "省核准判不予延续，全单止住");
            }
            if (!checkPassed) {
                // 印虽落下，旧证缴回那一笔未看过或尚有欠缴：此印不作数，仍挂省核准
                return new Decision(Outcome.WAIT, "省核准须先看过旧证缴回且无欠缴，挂在省核准");
            }
            return new Decision(Outcome.DONE, "省局点齐，核签单办结封印");
        }

        throw new IllegalArgumentException("无此档口: " + node);
    }
}
