package com.fc.v2.service.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fc.v2.mapper.auto.TSaltCertRegistryMapper;
import com.fc.v2.mapper.auto.TSaltEntMapper;
import com.fc.v2.mapper.auto.TSaltRenewBillMapper;
import com.fc.v2.mapper.auto.TSaltRenewCheckMapper;
import com.fc.v2.mapper.auto.TSaltRenewSignMapper;
import com.fc.v2.model.auto.TSaltCertRegistry;
import com.fc.v2.model.auto.TSaltEnt;
import com.fc.v2.model.auto.TSaltRenewBill;
import com.fc.v2.model.auto.TSaltRenewCheck;
import com.fc.v2.model.auto.TSaltRenewSign;
import com.fc.v2.service.ITSaltRenewBillService;
import com.fc.v2.service.renew.RenewActionResult;
import com.fc.v2.service.renew.RenewRuleException;
import com.fc.v2.service.renew.RenewRules;
import com.fc.v2.util.StringUtils;

/**
 * 延续换证核签单 Service业务层处理（approval-chain 形状：三级核签）。
 *
 * <p>本类只编排两本账：核签单（{@link TSaltRenewBill}）与受理簿（{@link TSaltRenewSign}
 * 落印流水），外加省档前的旧证核查笔与省局点齐同事务写入的发证名册。
 * 压在哪一关、让不让走、分歧停何处、办结封不封印，一律问 {@link RenewRules}，
 * 本类不自作主张。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TSaltRenewBillServiceImpl implements ITSaltRenewBillService {

    private static final String[] NODE_NAMES = {"县初核", "市复审", "省核准"};

    @javax.annotation.Resource
    private TSaltRenewBillMapper saltRenewBillMapper;
    @javax.annotation.Resource
    private TSaltRenewSignMapper saltRenewSignMapper;
    @javax.annotation.Resource
    private TSaltRenewCheckMapper saltRenewCheckMapper;
    @javax.annotation.Resource
    private TSaltCertRegistryMapper saltCertRegistryMapper;
    @javax.annotation.Resource
    private TSaltEntMapper saltEntMapper;

    @Override
    public TSaltRenewBill selectTSaltRenewBillById(Long id) {
        return this.saltRenewBillMapper.selectById(id);
    }

    @Override
    public List<TSaltRenewBill> selectTSaltRenewBillList(QueryWrapper<TSaltRenewBill> queryWrapper) {
        return this.saltRenewBillMapper.selectList(queryWrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TSaltRenewBill openBill(String billNo, String entNo) {
        if (StringUtils.isEmpty(billNo) || StringUtils.isEmpty(entNo)) {
            throw new RenewRuleException("核签单代号与所对企业代号都缺不得");
        }
        Integer dup = this.saltRenewBillMapper.selectCount(new QueryWrapper<TSaltRenewBill>()
                .eq("bill_no", billNo).eq("del_flag", 0));
        if (dup != null && dup > 0) {
            throw new RenewRuleException("核签单代号已立案，不得重复立案：" + billNo);
        }
        Integer entCnt = this.saltEntMapper.selectCount(new QueryWrapper<TSaltEnt>()
                .eq("site_no", entNo).eq("del_flag", 0).eq("status", 0));
        if (entCnt == null || entCnt == 0) {
            throw new RenewRuleException("所对批发企业不在在册名录中，立案被拒：" + entNo);
        }
        TSaltRenewBill bill = new TSaltRenewBill();
        bill.setBillNo(billNo);
        bill.setEntNo(entNo);
        bill.setNodeNo(RenewRules.NODE_COUNTY);
        bill.setCurRound(1);
        bill.setSignMode(RenewRules.modeOf(RenewRules.NODE_COUNTY));
        bill.setNeedCount(RenewRules.needCount(RenewRules.NODE_COUNTY));
        bill.setSignCount(0);
        bill.setStatus(RenewRules.STATUS_RUNNING);
        bill.setDelFlag(0);
        this.saltRenewBillMapper.insert(bill);
        return bill;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RenewActionResult sign(Long id, Integer claimedNode, String signer, Integer verdict, String comment) {
        // 行锁取单：同一单的并发落印在本事务里排队，落印—点数—裁决—推进/登记名册是一笔事务
        TSaltRenewBill bill = this.saltRenewBillMapper.selectByIdForUpdate(id);
        if (bill == null) {
            throw new RenewRuleException("核签单不存在或已删除");
        }
        if (StringUtils.isEmpty(signer)) {
            throw new RenewRuleException("落印人缺不得");
        }
        if (verdict == null
                || (verdict != RenewRules.VERDICT_RENEW && verdict != RenewRules.VERDICT_DENY)) {
            throw new RenewRuleException("判词只认：可延续/不予延续");
        }
        if (bill.getStatus() != null && bill.getStatus() == RenewRules.STATUS_DONE) {
            throw new RenewRuleException("省局已点齐，此单办结封印，谁也添不进印");
        }
        if (bill.getStatus() != null && bill.getStatus() == RenewRules.STATUS_VETOED) {
            throw new RenewRuleException("此单已被否决止住，往后各档再落不得印");
        }

        int node = bill.getNodeNo() == null ? RenewRules.NODE_COUNTY : bill.getNodeNo();
        int round = bill.getCurRound() == null ? 1 : bill.getCurRound();

        // 递单带来的关号不作结论，只当来意；与单据实际所压档口对不上，以单据为准、本次不认
        if (claimedNode != null && claimedNode.intValue() != node) {
            throw new RenewRuleException("递单关号(" + nodeName(claimedNode)
                    + ")与单据实际所压档口(" + nodeName(node) + ")不符，以单据为准，本次落印不认");
        }

        // 本档本轮已有点数：点齐或一肯一否挂起后都不得再添，出路只有本档送回上一档重走
        List<TSaltRenewSign> roundStamps = loadRoundStamps(id, round, node);
        if (roundStamps.size() >= RenewRules.needCount(node)) {
            throw new RenewRuleException(nodeName(node) + "应落" + RenewRules.needCount(node)
                    + "名，已点" + roundStamps.size() + "笔；若两名审查员各执一词，"
                    + "挂在本档待复议，由本档把单子送回上一档后重走，不得就地添印");
        }
        // 同一名审查员本轮本档不得落两笔
        for (TSaltRenewSign s : roundStamps) {
            if (signer.equals(s.getSigner())) {
                throw new RenewRuleException(signer + " 在本轮" + nodeName(node) + "已落过印，不得再落");
            }
        }

        // 省核准：先要看过旧证缴回那一笔，看过才落得下去
        Long checkId = null;
        boolean checkPassed = false;
        if (node == RenewRules.NODE_PROVINCE) {
            TSaltRenewCheck check = latestCheck(id, round);
            if (check == null) {
                throw new RenewRuleException("省核准须先看过旧证缴回那一笔，看过才落得下去");
            }
            if (verdict == RenewRules.VERDICT_RENEW
                    && (check.getOldCertReturned() == null
                            || check.getOldCertReturned() != 1)) {
                throw new RenewRuleException("尚有未缴回旧证，省核准判可延续落不下去");
            }
            checkId = check.getId();
            checkPassed = Integer.valueOf(1).equals(check.getOldCertReturned());
        }

        // 落印入受理簿（顺落印次序成行）
        TSaltRenewSign stamp = new TSaltRenewSign();
        stamp.setBillId(id);
        stamp.setBillNo(bill.getBillNo());
        stamp.setRoundNo(round);
        stamp.setNodeNo(node);
        stamp.setSigner(signer);
        stamp.setVerdict(verdict);
        stamp.setValid(RenewRules.SIGN_VALID);
        stamp.setCheckId(checkId);
        stamp.setDelFlag(0);
        stamp.setRemark(comment);
        this.saltRenewSignMapper.insert(stamp);

        // 已落几人不留人手格子：系统顺着落印次序当场逐笔点出，回填屏上那栏
        roundStamps = loadRoundStamps(id, round, node);
        int done = roundStamps.size();
        bill.setSignCount(done);
        bill.setNeedCount(RenewRules.needCount(node));
        bill.setSignMode(RenewRules.modeOf(node));

        boolean checkPassedFinal = checkPassed;
        RenewRules.Decision decision = RenewRules.decide(node, roundStamps, checkPassedFinal);

        switch (decision.getOutcome()) {
            case WAIT:
                bill.setStatus(RenewRules.STATUS_RUNNING);
                break;
            case VETO:
                // 哪一档把这张单否了，全单就此止住
                bill.setStatus(RenewRules.STATUS_VETOED);
                break;
            case ADVANCE:
                // 推进只许走紧邻的下一道；前一档点不齐时后档的印在裁决处根本进不来
                bill.setNodeNo(node + 1);
                bill.setSignCount(0);
                bill.setNeedCount(RenewRules.needCount(node + 1));
                bill.setSignMode(RenewRules.modeOf(node + 1));
                bill.setStatus(RenewRules.STATUS_RUNNING);
                break;
            case DONE:
                // 省局点齐：办结 + 发证名册登记，同一事务一次算出
                bill.setStatus(RenewRules.STATUS_DONE);
                registerRegistry(bill);
                break;
            default:
                throw new RenewRuleException("裁决处给了不认的去向");
        }

        int rows = this.saltRenewBillMapper.update(null, new UpdateWrapper<TSaltRenewBill>()
                .eq("id", id)
                .eq("status", RenewRules.STATUS_RUNNING)
                .eq("del_flag", 0)
                .set("node_no", bill.getNodeNo())
                .set("cur_round", round)
                .set("sign_mode", bill.getSignMode())
                .set("need_count", bill.getNeedCount())
                .set("sign_count", bill.getSignCount())
                .set("status", bill.getStatus())
                .set("update_time", new Date()));
        if (rows == 0) {
            // 并发下单子已被他人办结/否决：本次添印整笔回滚
            throw new RenewRuleException("单据状态已变，本次落印不作数");
        }
        return new RenewActionResult(bill, decision.getOutcome(), decision.getReason());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TSaltRenewBill recordCheck(Long id, String checker, Integer oldCertReturned, String comment) {
        TSaltRenewBill bill = this.saltRenewBillMapper.selectByIdForUpdate(id);
        if (bill == null) {
            throw new RenewRuleException("核签单不存在或已删除");
        }
        if (bill.getStatus() != null && bill.getStatus() != RenewRules.STATUS_RUNNING) {
            throw new RenewRuleException("此单已不在核，旧证核查笔登记不进去");
        }
        int node = bill.getNodeNo() == null ? 0 : bill.getNodeNo();
        if (node != RenewRules.NODE_PROVINCE) {
            throw new RenewRuleException("旧证缴回核查只在省核准档看，单子此刻压在" + nodeName(node));
        }
        if (StringUtils.isEmpty(checker)) {
            throw new RenewRuleException("核查人缺不得");
        }
        if (oldCertReturned == null || (oldCertReturned != 0 && oldCertReturned != 1)) {
            throw new RenewRuleException("旧证缴回情形只认：已缴回/尚有未缴回");
        }
        TSaltRenewCheck check = new TSaltRenewCheck();
        check.setBillId(id);
        check.setBillNo(bill.getBillNo());
        check.setRoundNo(bill.getCurRound());
        check.setOldCertReturned(oldCertReturned);
        check.setChecker(checker);
        check.setCheckTime(new Date());
        check.setDelFlag(0);
        check.setRemark(comment);
        this.saltRenewCheckMapper.insert(check);
        return bill;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RenewActionResult rollback(Long id, String comment) {
        TSaltRenewBill bill = this.saltRenewBillMapper.selectByIdForUpdate(id);
        if (bill == null) {
            throw new RenewRuleException("核签单不存在或已删除");
        }
        if (bill.getStatus() != null && bill.getStatus() == RenewRules.STATUS_DONE) {
            throw new RenewRuleException("省局已点齐办结，封印之单收不回");
        }
        if (bill.getStatus() != null && bill.getStatus() == RenewRules.STATUS_VETOED) {
            throw new RenewRuleException("已被否决止住的单，不走退回，全单已定");
        }
        int node = bill.getNodeNo() == null ? 0 : bill.getNodeNo();
        if (node == RenewRules.NODE_COUNTY) {
            throw new RenewRuleException("县初核是头一道，无上一档可退");
        }
        int round = bill.getCurRound() == null ? 1 : bill.getCurRound();

        // 往回收只收一格：本档本轮落下的印当场软清零、留底不抹；早先各档与往轮旧印一笔不动
        int cleared = this.saltRenewSignMapper.update(null, new UpdateWrapper<TSaltRenewSign>()
                .eq("bill_id", id)
                .eq("round_no", round)
                .eq("node_no", node)
                .eq("valid", RenewRules.SIGN_VALID)
                .eq("del_flag", 0)
                .set("valid", RenewRules.SIGN_CLEARED)
                .set("update_time", new Date()));

        int prevNode = node - 1;
        int nextRound = round + 1;
        int rows = this.saltRenewBillMapper.update(null, new UpdateWrapper<TSaltRenewBill>()
                .eq("id", id)
                .eq("status", RenewRules.STATUS_RUNNING)
                .eq("del_flag", 0)
                .set("node_no", prevNode)
                .set("cur_round", nextRound)
                .set("sign_mode", RenewRules.modeOf(prevNode))
                .set("need_count", RenewRules.needCount(prevNode))
                .set("sign_count", 0)
                .set("status", RenewRules.STATUS_RUNNING)
                .set("update_time", new Date()));
        if (rows == 0) {
            throw new RenewRuleException("单据状态已变，退回不作数");
        }
        bill.setNodeNo(prevNode);
        bill.setCurRound(nextRound);
        bill.setSignMode(RenewRules.modeOf(prevNode));
        bill.setNeedCount(RenewRules.needCount(prevNode));
        bill.setSignCount(0);
        bill.setStatus(RenewRules.STATUS_RUNNING);

        String reason = "由" + nodeName(node) + "送回" + nodeName(prevNode)
                + "，本档本轮" + cleared + "笔印当场清零留底，旧档旧印不动；自第"
                + nextRound + "轮从" + nodeName(prevNode) + "往上续";
        return new RenewActionResult(bill, RenewRules.Outcome.WAIT, reason);
    }

    @Override
    public Map<String, Object> board() {
        // 一次取全量，下面逐单调受理簿当场点：屏上的数与逐笔点数同一次遍历算出，不分两处数
        List<TSaltRenewBill> bills = this.saltRenewBillMapper.selectList(
                new QueryWrapper<TSaltRenewBill>().eq("del_flag", 0).orderByAsc("id"));
        List<Long> runningIds = new ArrayList<>();
        for (TSaltRenewBill b : bills) {
            if (b.getStatus() != null && b.getStatus() == RenewRules.STATUS_RUNNING) {
                runningIds.add(b.getId());
            }
        }
        Map<Long, List<TSaltRenewSign>> validStamps = loadValidStamps(runningIds);

        int[] stayByNode = new int[RenewRules.NODE_TOTAL];
        int runningFromColumn = 0;
        int doneCount = 0;
        int vetoedCount = 0;
        boolean allMatched = true;
        List<Map<String, Object>> rows = new ArrayList<>();

        for (TSaltRenewBill b : bills) {
            int status = b.getStatus() == null ? RenewRules.STATUS_RUNNING : b.getStatus();
            int node = b.getNodeNo() == null ? 0 : b.getNodeNo();
            int round = b.getCurRound() == null ? 1 : b.getCurRound();
            int columnCount = b.getSignCount() == null ? 0 : b.getSignCount();

            int ledgerCount = 0;
            boolean rowMatched = true;
            if (status == RenewRules.STATUS_RUNNING) {
                // 单据栏上的在办数：随立案加一、办结减一
                runningFromColumn++;
                // 逐档停留：当场逐单调受理簿点出本档有效笔数
                stayByNode[node]++;
                for (TSaltRenewSign s : validStamps.getOrDefault(b.getId(), new ArrayList<>())) {
                    if (s.getRoundNo() != null && s.getRoundNo() == round
                            && s.getNodeNo() != null && s.getNodeNo() == node) {
                        ledgerCount++;
                    }
                }
                rowMatched = ledgerCount == columnCount;
                if (!rowMatched) {
                    allMatched = false;
                }
            } else if (status == RenewRules.STATUS_DONE) {
                doneCount++;
            } else if (status == RenewRules.STATUS_VETOED) {
                vetoedCount++;
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", b.getId());
            row.put("billNo", b.getBillNo());
            row.put("entNo", b.getEntNo());
            row.put("nodeNo", node);
            row.put("nodeName", nodeName(node));
            row.put("curRound", round);
            row.put("needCount", b.getNeedCount());
            row.put("signCountShown", columnCount);
            row.put("signCountCounted", ledgerCount);
            row.put("countMatched", rowMatched);
            row.put("status", status);
            row.put("statusName", statusName(status));
            rows.add(row);
        }

        Map<String, Object> board = new LinkedHashMap<>();
        // 逐档点出来的在办张数：各关停留之和，与上面单据栏累计同一次遍历、同一个数
        int runningCountFromStays = stayByNode[0] + stayByNode[1] + stayByNode[2];
        board.put("totalCount", bills.size());
        board.put("runningCount", runningFromColumn);
        board.put("runningCountFromStays", runningCountFromStays);
        board.put("countMatched", allMatched && runningFromColumn == runningCountFromStays);
        Map<String, Object> stayNames = new LinkedHashMap<>();
        stayNames.put(NODE_NAMES[0], stayByNode[0]);
        stayNames.put(NODE_NAMES[1], stayByNode[1]);
        stayNames.put(NODE_NAMES[2], stayByNode[2]);
        board.put("stayByNode", stayByNode);
        board.put("stayNames", stayNames);
        board.put("doneCount", doneCount);
        board.put("vetoedCount", vetoedCount);
        board.put("rows", rows);
        return board;
    }

    @Override
    public Map<String, Object> audit(Long id) {
        TSaltRenewBill bill = this.saltRenewBillMapper.selectById(id);
        if (bill == null) {
            throw new RenewRuleException("核签单不存在或已删除");
        }
        List<TSaltRenewSign> stamps = this.saltRenewSignMapper.selectList(new QueryWrapper<TSaltRenewSign>()
                .eq("bill_id", id).eq("del_flag", 0).orderByAsc("id"));
        List<TSaltRenewCheck> checks = this.saltRenewCheckMapper.selectList(new QueryWrapper<TSaltRenewCheck>()
                .eq("bill_id", id).eq("del_flag", 0).orderByAsc("id"));

        int billStatus = bill.getStatus() == null ? RenewRules.STATUS_RUNNING : bill.getStatus();
        int billNode = bill.getNodeNo() == null ? 0 : bill.getNodeNo();
        int billRound = bill.getCurRound() == null ? 1 : bill.getCurRound();
        int maxRound = billRound;

        // 受理簿按轮、按档归块（顺落印次序）
        Map<Integer, Map<Integer, List<TSaltRenewSign>>> blocks = new LinkedHashMap<>();
        for (TSaltRenewSign s : stamps) {
            int r = s.getRoundNo() == null ? 1 : s.getRoundNo();
            int n = s.getNodeNo() == null ? 0 : s.getNodeNo();
            maxRound = Math.max(maxRound, r);
            blocks.computeIfAbsent(r, k -> new LinkedHashMap<>())
                    .computeIfAbsent(n, k -> new ArrayList<>()).add(s);
        }
        // 每轮的入口档取证：本轮最早落印（含清零留底）的那一档就是入口
        // （从被退回那一档往上续，续走必先在该档落印）；一轮一笔皆无只可能是
        // 当前轮，入口即单据此刻所压档。
        Map<Integer, Integer> entryNode = new HashMap<>();
        for (int r = 1; r <= maxRound; r++) {
            Integer entry = null;
            for (TSaltRenewSign s : stamps) {
                int sr = s.getRoundNo() == null ? 1 : s.getRoundNo();
                int sn = s.getNodeNo() == null ? 0 : s.getNodeNo();
                if (sr == r) {
                    entry = entry == null ? sn : Math.min(entry, sn);
                }
            }
            entryNode.put(r, entry == null ? billNode : entry);
        }

        List<Map<String, Object>> blockViews = new ArrayList<>();
        int totalValid = 0;
        int totalCleared = 0;
        // 自省局往回收、数到县里初核：轮次倒序，轮内省→市→县
        for (int r = maxRound; r >= 1; r--) {
            // 该轮走到过的最高档：有落印（含清零留底）的最高档；当前轮再把单子此刻所压档算上
            int upper = highestReached(blocks.get(r));
            if (r == billRound) {
                upper = Math.max(upper, billNode);
            }
            for (int n = RenewRules.NODE_TOTAL - 1; n >= 0; n--) {
                List<TSaltRenewSign> blockStamps = blocks.getOrDefault(r, new HashMap<>())
                        .getOrDefault(n, new ArrayList<>());

                List<TSaltRenewSign> validStamps = new ArrayList<>();
                int cleared = 0;
                List<Map<String, Object>> stampViews = new ArrayList<>();
                for (TSaltRenewSign s : blockStamps) {
                    boolean isCleared = s.getValid() != null && s.getValid() == RenewRules.SIGN_CLEARED;
                    if (isCleared) {
                        cleared++;
                    } else {
                        validStamps.add(s);
                    }
                    Map<String, Object> v = new LinkedHashMap<>();
                    v.put("signer", s.getSigner());
                    v.put("verdict", s.getVerdict());
                    v.put("verdictName", s.getVerdict() != null && s.getVerdict() == RenewRules.VERDICT_RENEW
                            ? "可延续" : "不予延续");
                    v.put("validName", isCleared ? "本轮退回清零留底" : "有效");
                    v.put("stampTime", s.getCreateTime());
                    stampViews.add(v);
                }
                totalValid += validStamps.size();
                totalCleared += cleared;

                List<Map<String, Object>> checkViews = new ArrayList<>();
                boolean checkPassed = false;
                for (TSaltRenewCheck c : checks) {
                    int cr = c.getRoundNo() == null ? 1 : c.getRoundNo();
                    if (cr == r && n == RenewRules.NODE_PROVINCE) {
                        if (Integer.valueOf(1).equals(c.getOldCertReturned())) {
                            checkPassed = true;
                        }
                        Map<String, Object> v = new LinkedHashMap<>();
                        v.put("checker", c.getChecker());
                        v.put("oldCertReturnedName",
                                Integer.valueOf(1).equals(c.getOldCertReturned()) ? "已缴回" : "尚有未缴回");
                        v.put("checkTime", c.getCheckTime());
                        checkViews.add(v);
                    }
                }

                // 任取一块单看也经得起复核：交回唯一裁决处判一遍
                String blockState;
                RenewRules.Outcome outcome = null;
                String ruleReason = null;
                int entry = entryNode.getOrDefault(r, 0);
                boolean reached = n >= entry && n <= upper;
                if (r == billRound && billStatus == RenewRules.STATUS_RUNNING && n > billNode) {
                    reached = false;
                }
                if (cleared > 0) {
                    blockState = "本档送回上一档，当轮落印已当场清零留底";
                } else if (!reached) {
                    blockState = "未走到";
                } else if (validStamps.isEmpty()) {
                    blockState = "已走到，待落印";
                } else {
                    RenewRules.Decision decision = RenewRules.decide(n, validStamps, checkPassed);
                    outcome = decision.getOutcome();
                    ruleReason = decision.getReason();
                    if (r == billRound && billStatus == RenewRules.STATUS_RUNNING && n == billNode) {
                        switch (outcome) {
                            case WAIT:
                                blockState = n == RenewRules.NODE_CITY && validStamps.size() == 2
                                        ? "两名审查员各执一词，挂在本档待复议" : "在办，挂在本档";
                                break;
                            default:
                                blockState = "在办";
                        }
                    } else {
                        switch (outcome) {
                            case ADVANCE:
                                blockState = "同档算齐，已放行进下一档";
                                break;
                            case DONE:
                                blockState = "省局点齐，办结封印";
                                break;
                            case VETO:
                                blockState = "本档判否，全单就此止住";
                                break;
                            default:
                                blockState = "挂在本档待续";
                        }
                    }
                }

                Map<String, Object> block = new LinkedHashMap<>();
                block.put("roundNo", r);
                block.put("nodeNo", n);
                block.put("nodeName", NODE_NAMES[n]);
                block.put("needCount", RenewRules.needCount(n));
                block.put("mode", RenewRules.modeOf(n));
                block.put("validCount", validStamps.size());
                block.put("clearedCount", cleared);
                block.put("reached", reached);
                block.put("outcome", outcome);
                block.put("ruleReason", ruleReason);
                block.put("blockState", blockState);
                block.put("stamps", stampViews);
                block.put("checks", checkViews);
                blockViews.add(block);
            }
        }

        // 笔数与当日受理簿一笔不差：总数 = 各块有效笔数 + 各块清零笔数
        int ledgerTotal = stamps.size();
        boolean ledgerMatched = ledgerTotal == totalValid + totalCleared;
        // 核签结果与发证名册出自同一次算：办结则名册恰有此单一笔，未办结则一笔没有
        int registryCount = this.saltCertRegistryMapper.selectCount(new QueryWrapper<TSaltCertRegistry>()
                .eq("bill_no", bill.getBillNo()).eq("del_flag", 0));
        boolean registryMatched = billStatus == RenewRules.STATUS_DONE ? registryCount == 1 : registryCount == 0;

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("billId", bill.getId());
        audit.put("billNo", bill.getBillNo());
        audit.put("entNo", bill.getEntNo());
        audit.put("curRound", billRound);
        audit.put("nodeNo", billNode);
        audit.put("status", billStatus);
        audit.put("statusName", statusName(billStatus));
        audit.put("ledgerTotal", ledgerTotal);
        audit.put("totalValid", totalValid);
        audit.put("totalCleared", totalCleared);
        audit.put("ledgerMatched", ledgerMatched);
        audit.put("registryCount", registryCount);
        audit.put("registryMatched", registryMatched);
        audit.put("blocks", blockViews);
        return audit;
    }

    @Override
    public List<TSaltRenewSign> ledger(Long id) {
        return this.saltRenewSignMapper.selectList(new QueryWrapper<TSaltRenewSign>()
                .eq("bill_id", id).eq("del_flag", 0).orderByAsc("id"));
    }

    @Override
    public List<TSaltCertRegistry> listRegistry() {
        return this.saltCertRegistryMapper.selectList(new QueryWrapper<TSaltCertRegistry>()
                .eq("del_flag", 0).orderByAsc("id"));
    }

    // ------------------------------------------------------------------

    /** 本轮走到过的最高档：有任何落印（含清零留底）的最大档号；一笔皆无给县初核 */
    private int highestReached(Map<Integer, List<TSaltRenewSign>> roundBlocks) {
        int high = RenewRules.NODE_COUNTY;
        if (roundBlocks != null) {
            for (Integer n : roundBlocks.keySet()) {
                high = Math.max(high, n == null ? 0 : n);
            }
        }
        return high;
    }

    /** 本档本轮逐笔点出的有效落印，顺落印次序（id 即落印先后） */
    private List<TSaltRenewSign> loadRoundStamps(Long billId, int round, int node) {
        return this.saltRenewSignMapper.selectList(new QueryWrapper<TSaltRenewSign>()
                .eq("bill_id", billId)
                .eq("round_no", round)
                .eq("node_no", node)
                .eq("valid", RenewRules.SIGN_VALID)
                .eq("del_flag", 0)
                .orderByAsc("id"));
    }

    /** 一批在办单的全部有效落印，一次取回内存归类，桌面清点只走这一回查询 */
    private Map<Long, List<TSaltRenewSign>> loadValidStamps(List<Long> billIds) {
        Map<Long, List<TSaltRenewSign>> map = new HashMap<>();
        if (billIds.isEmpty()) {
            return map;
        }
        List<TSaltRenewSign> all = this.saltRenewSignMapper.selectList(new QueryWrapper<TSaltRenewSign>()
                .in("bill_id", billIds)
                .eq("valid", RenewRules.SIGN_VALID)
                .eq("del_flag", 0)
                .orderByAsc("id"));
        for (TSaltRenewSign s : all) {
            map.computeIfAbsent(s.getBillId(), k -> new ArrayList<>()).add(s);
        }
        return map;
    }

    /** 本轮省档旧证核查的最近一笔（补正后可再看，以最近那笔为准） */
    private TSaltRenewCheck latestCheck(Long billId, int round) {
        List<TSaltRenewCheck> list = this.saltRenewCheckMapper.selectList(new QueryWrapper<TSaltRenewCheck>()
                .eq("bill_id", billId)
                .eq("round_no", round)
                .eq("del_flag", 0)
                .orderByDesc("id")
                .last("limit 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    /** 省局点齐的同一事务里登记发证名册：核签结果与名册同一次算出，名册无手填入口 */
    private void registerRegistry(TSaltRenewBill bill) {
        Integer exists = this.saltCertRegistryMapper.selectCount(new QueryWrapper<TSaltCertRegistry>()
                .eq("bill_no", bill.getBillNo()).eq("del_flag", 0));
        if (exists != null && exists > 0) {
            throw new RenewRuleException("发证名册已有此单，不得重复登记");
        }
        TSaltCertRegistry registry = new TSaltCertRegistry();
        registry.setBillNo(bill.getBillNo());
        registry.setEntNo(bill.getEntNo());
        registry.setIssueNo("FX-" + bill.getBillNo());
        registry.setIssueTime(new Date());
        registry.setDelFlag(0);
        this.saltCertRegistryMapper.insert(registry);
    }

    private static String nodeName(int node) {
        return node >= 0 && node < NODE_NAMES.length ? NODE_NAMES[node] : ("第" + node + "关");
    }

    private static String statusName(int status) {
        if (status == RenewRules.STATUS_DONE) {
            return "已办结";
        }
        if (status == RenewRules.STATUS_VETOED) {
            return "已否决";
        }
        return "在核";
    }
}
