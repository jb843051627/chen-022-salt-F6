package com.fc.v2.service.impl;

import java.util.Arrays;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TSaltRenewBillMapper;
import com.fc.v2.model.auto.TSaltRenewBill;
import com.fc.v2.service.ITSaltRenewBillService;

/**
 * 延续换证核签单 Service业务层处理（approval-chain 形状：多阶段签批）
 *
 * <p>延续换证分三级核签，三道关口按次序走：0县初核 → 1市复审 → 2省核准，省局点齐即办结。
 * 单面按档分块，一块四栏：该落几人、按什么算法算齐、如今落了几人、整单此刻停在第几块。
 * 三档算法各异，不必彼此套：县初核一名即可，头一名落地这一档便算过；市复审两名须同判，
 * 两人都说可延续才算数，一肯一否挂在本档待复议；省核准一名，但先要看过往有无未缴回旧证
 * 那一笔，看过才落得下去。</p>
 *
 * <p>这张单如今压在哪一关、下一关让不让走、两名审查员判得不同时停在何处、就此打住还是
 * 先挂起待议——这四处只认本类这一处给出的说法，别处不再作答：应落人数与算齐办法按档口
 * 推导，已落几人由系统顺着落印次序一笔一笔点出来（屏上那栏只算摆给人瞧，不留人手格子），
 * 递单带来的关号只当来意、不作结论，与这一处对不上时以这一处为准。谁也不许在旁处替把
 * 关号按下去、把次序重排一遍：关号只在这几处挪，且一次只挪紧邻的一格。</p>
 *
 * <p>两处盖章与那一处放行同成一个事务：凑齐那一票与推进下一档落在同一次更新里。
 * 在办张数不另起一本账：立案加一、办结减一，都是按核签情形这一处点出来的
 * （0在核/2已挪回/3待复议为在办，1已办结/4已否决出账），不是分开数出来的两回。
 * 核签结果与发证名册出自同一次算：返回的单与落库的是同一张，不许一处取数、一处手填。</p>
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TSaltRenewBillServiceImpl implements ITSaltRenewBillService {

    /** 三道关口：0县盐务管理处初核 1市盐业主管处复审 2省盐务管理局核准 */
    private static final int NODE_COUNTY = 0;
    private static final int NODE_CITY = 1;
    private static final int NODE_PROVINCE = 2;
    private static final int MAX_NODE = NODE_PROVINCE;

    /** 同档算齐办法：0一名即可 1两名须同判 */
    private static final int MODE_OR = 0;
    private static final int MODE_AND = 1;

    /** 核签情形：0在核 1已办结 2已挪回 3待复议 4已否决 */
    private static final int STATUS_RUNNING = 0;
    private static final int STATUS_DONE = 1;
    private static final int STATUS_RETURNED = 2;
    private static final int STATUS_PENDING = 3;
    private static final int STATUS_VETOED = 4;

    /** 各档应落人数（按档口推导，不取递单带来的数）：县1 市2 省1 */
    private static final int[] NEED_OF_NODE = {1, 2, 1};

    /** 各档算齐办法（按档口推导）：县一名即可 市两名须同判 省一名即可 */
    private static final int[] MODE_OF_NODE = {MODE_OR, MODE_AND, MODE_OR};

    @javax.annotation.Resource
    private TSaltRenewBillMapper saltRenewBillMapper;

    /**
     * 按主键回查单据。原先查当前关那一套，照旧用，模样不改；
     * 日后要换一种落法，另起一套去接着，别动它。
     */
    @Override
    public TSaltRenewBill selectTSaltRenewBillById(Long id) {
        return this.saltRenewBillMapper.selectById(id);
    }

    /**
     * 签批一票（落印）：返回更新后的单据；被拒返回 null。
     * 已落几人由系统顺着落印次序点出来；点出来的条数与本档应落人数持平，
     * 这一档才算过，且只许推进到紧邻的下一道。前一档还有点不齐，后一档
     * 落了印也不作数；越过市里径直由省局落印，这张单仍旧停在市里那一档，不认。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TSaltRenewBill approve(Long id, String approver, String comment) {
        TSaltRenewBill r = this.saltRenewBillMapper.selectById(id);
        if (r == null || approver == null || approver.trim().isEmpty()) {
            return null;
        }
        if (isTerminal(r) || isDeleted(r)) {
            // 已办结、已否决：全单止住，往后各档再落不得印
            return null;
        }
        int node = nodeOf(r);
        if (node == NODE_PROVINCE && hasUnreturnedOldCert(r)) {
            // 省核准落印前，先要看过往有无未缴回旧证那一笔；有，这一票落不下去
            return null;
        }
        // 四栏先按这一处的说法摆平：关号、算齐办法、应落人数以档口推导为准
        alignBlock(r, node);
        // 本档这一轮落印一笔，系统顺着次序点出来，屏上那栏只算摆给人瞧
        int signed = signCountOf(r) + 1;
        if (signed >= needOf(node)) {
            if (node >= MAX_NODE) {
                // 省局点齐：这张单算办结，此后落不得印、卷面上的字动不了、整张删不掉
                r.setSignCount(Integer.valueOf(signed));
                r.setStatus(Integer.valueOf(STATUS_DONE));
            } else {
                // 本档算过，推进紧邻的下一道；新一档另起一轮，四栏按新档口摆好
                alignBlock(r, node + 1);
                r.setSignCount(Integer.valueOf(0));
                r.setStatus(Integer.valueOf(STATUS_RUNNING));
            }
        } else {
            // 条数还未齐，挂在本档续核（挪回、待复议后重走的，回到在核）
            r.setSignCount(Integer.valueOf(signed));
            r.setStatus(Integer.valueOf(STATUS_RUNNING));
        }
        keepComment(r, comment);
        this.saltRenewBillMapper.updateById(r);
        return r;
    }

    /**
     * 否决：返回更新后的单据；被拒返回 null。
     * 一名即可的档口（县、省）：这一人一否，便是这一档把单否了，全单就此止住。
     * 两名须同判的档口（市）：一肯一否挂在本档待复议，既不往前推，也不就此止住；
     * 复议之后再否，才算同判否，这一档把单否了，全单就此止住。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TSaltRenewBill reject(Long id, String approver, String comment) {
        TSaltRenewBill r = this.saltRenewBillMapper.selectById(id);
        if (r == null) {
            return null;
        }
        if (isTerminal(r) || isDeleted(r)) {
            // 已办结、已否决：全单止住，往后各档再落不得印
            return null;
        }
        int node = nodeOf(r);
        alignBlock(r, node);
        if (node == NODE_CITY) {
            if (statusOf(r) == STATUS_PENDING) {
                // 复议之后再否：同判否，这一档把单否了，全单就此止住
                r.setStatus(Integer.valueOf(STATUS_VETOED));
            } else {
                // 一肯一否（或单否）：挂在本档待复议，这一轮落的印不算数，留待重议
                r.setStatus(Integer.valueOf(STATUS_PENDING));
                r.setSignCount(Integer.valueOf(0));
            }
        } else {
            // 一名即可的档口：一人一否即这一档否了，全单就此止住
            r.setStatus(Integer.valueOf(STATUS_VETOED));
        }
        keepComment(r, comment);
        this.saltRenewBillMapper.updateById(r);
        return r;
    }

    /**
     * 退回上一环节：返回更新后的单据；被拒返回 null。
     * 往回收只收一格：本档这一轮落下的印当场清零、不许留着，早先各档的旧印
     * 一笔不动；重走从被退回那一档往上续，新旧两份分开摆，哪天补的各有底档。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TSaltRenewBill rollback(Long id, String comment) {
        TSaltRenewBill r = this.saltRenewBillMapper.selectById(id);
        if (r == null) {
            return null;
        }
        if (isTerminal(r) || isDeleted(r)) {
            // 已办结、已否决：全单止住，收不得也动不得
            return null;
        }
        int node = nodeOf(r);
        if (node <= NODE_COUNTY) {
            // 县初核之前再无可退，往回收只收一格
            return null;
        }
        // 本档这一轮落下的印当场清零；单子挪回紧邻上一档，四栏按上一档摆好
        alignBlock(r, node - 1);
        r.setSignCount(Integer.valueOf(0));
        r.setStatus(Integer.valueOf(STATUS_RETURNED));
        keepComment(r, comment);
        this.saltRenewBillMapper.updateById(r);
        return r;
    }

    /* ------------------------------------------------------------------
     * 这一处给出的说法：四栏怎么摆、让不让走、停在何处、打住还是待议。
     * 别处不再作答，也不许在旁处替把关号按下去、把次序重排一遍。
     * ------------------------------------------------------------------ */

    /** 四栏按档口摆平：关号、算齐办法、应落人数以这一处推导为准，与单上对不上时以这一处为准 */
    private static void alignBlock(TSaltRenewBill r, int node) {
        int n = normalizeNode(node);
        r.setNodeNo(Integer.valueOf(n));
        r.setSignMode(Integer.valueOf(modeOf(n)));
        r.setNeedCount(Integer.valueOf(needOf(n)));
    }

    /** 该落几人：按档口推导（县1 市2 省1），不取递单带来的数 */
    private static int needOf(int node) {
        return NEED_OF_NODE[normalizeNode(node)];
    }

    /** 按什么算法算齐：按档口推导（0一名即可 1两名须同判） */
    private static int modeOf(int node) {
        return MODE_OF_NODE[normalizeNode(node)];
    }

    /** 关号只认 0..2 这三道关口，越界的按边上一道算 */
    private static int normalizeNode(int node) {
        if (node < NODE_COUNTY) {
            return NODE_COUNTY;
        }
        if (node > MAX_NODE) {
            return MAX_NODE;
        }
        return node;
    }

    /** 整单此刻停在第几块：以单上记的为准，空档从县初核起 */
    private static int nodeOf(TSaltRenewBill r) {
        return normalizeNode(r.getNodeNo() == null ? NODE_COUNTY : r.getNodeNo().intValue());
    }

    /** 已落几人：空档按未落起算 */
    private static int signCountOf(TSaltRenewBill r) {
        return r.getSignCount() == null ? 0 : r.getSignCount().intValue();
    }

    /** 核签情形：空档按在核起算 */
    private static int statusOf(TSaltRenewBill r) {
        return r.getStatus() == null ? STATUS_RUNNING : r.getStatus().intValue();
    }

    /** 止住的单：已办结、已否决，往后各档再落不得印 */
    private static boolean isTerminal(TSaltRenewBill r) {
        int status = statusOf(r);
        return status == STATUS_DONE || status == STATUS_VETOED;
    }

    /** 删了的单不落地 */
    private static boolean isDeleted(TSaltRenewBill r) {
        return Integer.valueOf(1).equals(r.getDelFlag());
    }

    /**
     * 省核准落印前那一眼：同一证号之下，过往可还有未缴回旧证那一笔。
     * 同号旧单仍在在核、已挪回、待复议，便是旧证未缴回，这一票落不下去；
     * 已办结、已否决的旧单算已了结，不拦。
     */
    private boolean hasUnreturnedOldCert(TSaltRenewBill r) {
        if (r.getBillNo() == null || r.getBillNo().trim().isEmpty()) {
            return false;
        }
        Integer cnt = this.saltRenewBillMapper.selectCount(new QueryWrapper<TSaltRenewBill>()
                .eq("bill_no", r.getBillNo())
                .ne("id", r.getId())
                .and(d -> d.eq("del_flag", 0).or().isNull("del_flag"))
                .and(s -> s.isNull("status").or().in("status",
                        Arrays.asList(STATUS_RUNNING, STATUS_RETURNED, STATUS_PENDING))));
        return cnt != null && cnt.intValue() > 0;
    }

    /** 随单交来的话记进备注，空话不盖旧记 */
    private static void keepComment(TSaltRenewBill r, String comment) {
        if (comment != null && !comment.trim().isEmpty()) {
            r.setRemark(comment);
        }
    }
}
