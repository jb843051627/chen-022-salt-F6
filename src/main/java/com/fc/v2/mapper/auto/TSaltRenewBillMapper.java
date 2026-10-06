package com.fc.v2.mapper.auto;

import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fc.v2.model.auto.TSaltRenewBill;

/**
 * 延续换证核签单数据层
 *
 * @author fuce
 * @date 2026-09-12
 */
public interface TSaltRenewBillMapper extends BaseMapper<TSaltRenewBill> {

    /**
     * 行锁取单：落印/退回/核查在同一事务里先锁单再动点数，
     * 把"落印—逐笔点—裁决—推进/登记名册"收成一笔。
     */
    TSaltRenewBill selectByIdForUpdate(@Param("id") Long id);
}
