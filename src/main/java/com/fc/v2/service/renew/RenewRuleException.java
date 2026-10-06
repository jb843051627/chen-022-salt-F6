package com.fc.v2.service.renew;

/**
 * 核签规则被挡下时抛出：印落不下去、关走不通、单已封印等。
 * 消息即唯一裁决处（{@link RenewRules}）给人的说法，控制器原样回给屏上。
 *
 * @author fuce
 * @date 2026-10-05
 */
public class RenewRuleException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public RenewRuleException(String message) {
        super(message);
    }
}
