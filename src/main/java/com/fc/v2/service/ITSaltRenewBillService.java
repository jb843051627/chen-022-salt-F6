package com.fc.v2.service;

import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.model.auto.TSaltCertRegistry;
import com.fc.v2.model.auto.TSaltRenewBill;
import com.fc.v2.model.auto.TSaltRenewSign;
import com.fc.v2.service.renew.RenewActionResult;

/**
 * 延续换证核签单 Service接口（approval-chain 形状：三级核签，无手工改栏/删单入口）。
 *
 * <p>压在哪一关、下一关让不让走、两名审查员分歧时停在何处、省局点齐后封不封印，
 * 全由 {@code com.fc.v2.service.renew.RenewRules} 一处裁决，本接口只编排落印流水
 * 与单据两本账。
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITSaltRenewBillService {

    /** 按主键回查单据（查当前关那一套照旧，模样不改） */
    TSaltRenewBill selectTSaltRenewBillById(Long id);

    /** 台账查询 */
    List<TSaltRenewBill> selectTSaltRenewBillList(QueryWrapper<TSaltRenewBill> queryWrapper);

    /**
     * 立案：新开一张核签单压在县初核，当前轮次为1。在办张数随立案加一。
     * 代号重复则被拒（返回 null）。
     */
    TSaltRenewBill openBill(String billNo, String entNo);

    /**
     * 落一笔记。claimedNode 是递单带来的关号，只当来意；与单据实际所压档口
     * 对不上时以单据为准、本次落印不认。返回唯一裁决处给的去向与说法；
     * 印落不下去（已否决/已办结/旧证未看/关号不符等）抛 RenewRuleException。
     */
    RenewActionResult sign(Long id, Integer claimedNode, String signer, Integer verdict, String comment);

    /**
     * 省核准前登记旧证缴回核查那一笔（看过才落得下去）。
     * 单据须正压在省核准档；oldCertReturned=1 无欠缴，省档才放得下印。
     */
    TSaltRenewBill recordCheck(Long id, String checker, Integer oldCertReturned, String comment);

    /**
     * 把单子送回紧邻的上一档：本档本轮落下的印当场清零（流水留底不抹），
     * 早先各档旧印一笔不动，轮次加一，从被退回那一档重走。
     */
    RenewActionResult rollback(Long id, String comment);

    /**
     * 桌面：一次取全量、逐单调流水当场点——在办张数（随立案加一、办结减一）、
     * 各档停留张数、屏上计数与逐笔点数是否合得上，都出自这同一次清点。
     */
    Map<String, Object> board();

    /**
     * 事后翻查：自省局往回收、数到县里初核，一块一块清点；新旧各轮分开摆，
     * 每块的有效笔数、已清零笔数与受理簿（落印流水）一笔不差，任取一块都经
     * 唯一裁决处复核。返回全单头（情形/轮次/笔数对账）与各块。
     */
    Map<String, Object> audit(Long id);

    /** 调阅某单受理簿：顺落印次序的全部底档（含退回清零的旧笔） */
    List<TSaltRenewSign> ledger(Long id);

    /** 发证名册只读：省局点齐同一事务登记，此处不设手填入口 */
    List<TSaltCertRegistry> listRegistry();
}
