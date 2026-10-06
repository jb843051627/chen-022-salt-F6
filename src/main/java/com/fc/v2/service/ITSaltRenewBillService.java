package com.fc.v2.service;

import com.fc.v2.model.auto.TSaltRenewBill;

/**
 * 延续换证核签单 Service接口（approval-chain 形状：多阶段签批，无增删改查入口）
 *
 * <p>三级核签按次序走：0县初核（一名即可）→ 1市复审（两名须同判）→ 2省核准
 * （一名，落印前先看过往有无未缴回旧证）。核签情形：0在核 1已办结 2已挪回
 * 3待复议 4已否决。已办结、已否决即为止住：落不得印、收不得格、字动不了、
 * 张删不掉。在办张数不另记账，就按核签情形这一处点出来。</p>
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITSaltRenewBillService {

    /** 按主键回查单据（原先查当前关那一套，照旧用，模样不改） */
    TSaltRenewBill selectTSaltRenewBillById(Long id);

    /** 签批一票：返回更新后的单据；被拒返回 null */
    TSaltRenewBill approve(Long id, String approver, String comment);

    /** 否决：返回更新后的单据；被拒返回 null */
    TSaltRenewBill reject(Long id, String approver, String comment);

    /** 退回上一环节：返回更新后的单据；被拒返回 null */
    TSaltRenewBill rollback(Long id, String comment);
}
