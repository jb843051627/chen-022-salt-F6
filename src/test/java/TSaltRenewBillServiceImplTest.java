import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TSaltRenewBillMapper;
import com.fc.v2.model.auto.TSaltRenewBill;
import com.fc.v2.service.ITSaltRenewBillService;
import com.fc.v2.service.impl.TSaltRenewBillServiceImpl;

/**
 * 延续换证核签单状态机核验：三级核签、同档算齐、挪回、否决、办结封口。
 * 不启 Spring，直接以 Mockito 顶替数据层，按落印次序一笔一笔点。
 */
public class TSaltRenewBillServiceImplTest {

    private static final int NODE_COUNTY = 0;
    private static final int NODE_CITY = 1;
    private static final int NODE_PROVINCE = 2;

    private static final int MODE_OR = 0;
    private static final int MODE_AND = 1;

    private static final int STATUS_RUNNING = 0;
    private static final int STATUS_DONE = 1;
    private static final int STATUS_RETURNED = 2;
    private static final int STATUS_PENDING = 3;
    private static final int STATUS_VETOED = 4;

    private TSaltRenewBillMapper mapper;
    private ITSaltRenewBillService service;

    /** 假库：按主键存单，updateById 落回 */
    private Map<Long, TSaltRenewBill> db;
    /** 同号旧单未了几笔（省核准那一眼） */
    private int danglingOldBills;

    @BeforeEach
    public void setUp() {
        db = new HashMap<Long, TSaltRenewBill>();
        danglingOldBills = 0;
        mapper = Mockito.mock(TSaltRenewBillMapper.class);
        lenient().when(mapper.selectById(any(Long.class))).thenAnswer(inv -> db.get(inv.getArgument(0)));
        lenient().when(mapper.updateById(any(TSaltRenewBill.class))).thenAnswer(inv -> {
            TSaltRenewBill r = inv.getArgument(0);
            db.put(r.getId(), r);
            return 1;
        });
        lenient().when(mapper.selectCount(any(QueryWrapper.class))).thenAnswer(inv -> danglingOldBills);
        service = new TSaltRenewBillServiceImpl();
        ReflectionTestUtils.setField(service, "saltRenewBillMapper", mapper);
    }

    private TSaltRenewBill file(Long id, String billNo, Integer node, Integer mode, Integer need,
                                Integer signed, Integer status) {
        TSaltRenewBill r = new TSaltRenewBill();
        r.setId(id);
        r.setBillNo(billNo);
        r.setNodeNo(node);
        r.setSignMode(mode);
        r.setNeedCount(need);
        r.setSignCount(signed);
        r.setStatus(status);
        r.setDelFlag(0);
        db.put(id, r);
        return r;
    }

    /** 四栏对读：整单停在第几块、按什么算法、该落几人、如今落了几人、核签情形 */
    private void assertBlock(TSaltRenewBill r, int node, int mode, int need, int signed, int status) {
        assertEquals(Integer.valueOf(node), r.getNodeNo(), "整单停在第几块");
        assertEquals(Integer.valueOf(mode), r.getSignMode(), "按什么算法算齐");
        assertEquals(Integer.valueOf(need), r.getNeedCount(), "该落几人");
        assertEquals(Integer.valueOf(signed), r.getSignCount(), "如今落了几人");
        assertEquals(Integer.valueOf(status), r.getStatus(), "核签情形");
    }

    @Test
    public void happyPath_threeStagesInOrder_thenSealed() {
        TSaltRenewBill r = file(1L, "XU-2026-001", NODE_COUNTY, MODE_OR, 1, 0, STATUS_RUNNING);

        // 县初核：一名即可，头一名落地这一档便算过，推进紧邻的市复审
        TSaltRenewBill back = service.approve(1L, "县初核员", "可延续");
        assertSame(r, back);
        assertBlock(r, NODE_CITY, MODE_AND, 2, 0, STATUS_RUNNING);

        // 市复审：两名须同判，第一票还不够
        back = service.approve(1L, "市审查员甲", "可延续");
        assertSame(r, back);
        assertBlock(r, NODE_CITY, MODE_AND, 2, 1, STATUS_RUNNING);

        // 第二票同判可延续：两处盖章与那一处放行同成一次，进省核准
        back = service.approve(1L, "市审查员乙", "可延续");
        assertSame(r, back);
        assertBlock(r, NODE_PROVINCE, MODE_OR, 1, 0, STATUS_RUNNING);

        // 省核准：一名，过往无未缴回旧证，落印即办结
        back = service.approve(1L, "省核准员", "可延续");
        assertSame(r, back);
        assertBlock(r, NODE_PROVINCE, MODE_OR, 1, 1, STATUS_DONE);

        // 办结之后：落不得印、否不得、收不得，卷面原样
        assertNull(service.approve(1L, "旁人", "再添"));
        assertNull(service.reject(1L, "旁人", "再否"));
        assertNull(service.rollback(1L, "再收"));
        assertBlock(r, NODE_PROVINCE, MODE_OR, 1, 1, STATUS_DONE);
    }

    @Test
    public void citySplitVote_hangsPending_thenReReviewPasses() {
        TSaltRenewBill r = file(2L, "XU-2026-002", NODE_CITY, MODE_AND, 2, 0, STATUS_RUNNING);

        service.approve(2L, "市审查员甲", "可延续");
        assertBlock(r, NODE_CITY, MODE_AND, 2, 1, STATUS_RUNNING);

        // 一肯一否：挂在本档待复议，既不往前推，也不就此止住
        TSaltRenewBill back = service.reject(2L, "市审查员乙", "暂缓");
        assertSame(r, back);
        assertBlock(r, NODE_CITY, MODE_AND, 2, 0, STATUS_PENDING);

        // 复议重议：两名同判可延续，仍从本档往上走
        service.approve(2L, "市审查员甲", "复议可延续");
        assertBlock(r, NODE_CITY, MODE_AND, 2, 1, STATUS_RUNNING);
        service.approve(2L, "市审查员乙", "复议可延续");
        assertBlock(r, NODE_PROVINCE, MODE_OR, 1, 0, STATUS_RUNNING);
    }

    @Test
    public void cityDoubleReject_vetoesWholeBill() {
        TSaltRenewBill r = file(3L, "XU-2026-003", NODE_CITY, MODE_AND, 2, 0, STATUS_RUNNING);

        // 单否先挂起待复议
        service.reject(3L, "市审查员甲", "不可");
        assertBlock(r, NODE_CITY, MODE_AND, 2, 0, STATUS_PENDING);

        // 复议再否：同判否，这一档把单否了，全单就此止住
        service.reject(3L, "市审查员乙", "不可");
        assertBlock(r, NODE_CITY, MODE_AND, 2, 0, STATUS_VETOED);

        // 止住之后：落不得印、收不得格
        assertNull(service.approve(3L, "市审查员甲", "可延续"));
        assertNull(service.rollback(3L, "收"));
        assertBlock(r, NODE_CITY, MODE_AND, 2, 0, STATUS_VETOED);
    }

    @Test
    public void orStage_singleRejectVetoes() {
        TSaltRenewBill county = file(4L, "XU-2026-004", NODE_COUNTY, MODE_OR, 1, 0, STATUS_RUNNING);
        service.reject(4L, "县初核员", "不可延续");
        assertBlock(county, NODE_COUNTY, MODE_OR, 1, 0, STATUS_VETOED);
        assertNull(service.approve(4L, "县初核员", "可延续"));

        TSaltRenewBill province = file(5L, "XU-2026-005", NODE_PROVINCE, MODE_OR, 1, 0, STATUS_RUNNING);
        service.reject(5L, "省核准员", "不可延续");
        assertBlock(province, NODE_PROVINCE, MODE_OR, 1, 0, STATUS_VETOED);
        assertNull(service.approve(5L, "省核准员", "可延续"));
    }

    @Test
    public void rollback_onlyOneStep_clearsCurrentRound_keepsEarlierStages() {
        TSaltRenewBill r = file(6L, "XU-2026-006", NODE_PROVINCE, MODE_OR, 1, 0, STATUS_RUNNING);

        // 省局把单子送回市复审：往回收只收一格，本档这一轮落印清零
        TSaltRenewBill back = service.rollback(6L, "材料不齐，退回");
        assertSame(r, back);
        assertBlock(r, NODE_CITY, MODE_AND, 2, 0, STATUS_RETURNED);

        // 市复审再送回县初核
        service.rollback(6L, "再退");
        assertBlock(r, NODE_COUNTY, MODE_OR, 1, 0, STATUS_RETURNED);

        // 县初核之前再无可退
        assertNull(service.rollback(6L, "退无可退"));
        assertBlock(r, NODE_COUNTY, MODE_OR, 1, 0, STATUS_RETURNED);

        // 重走从被退回那一档往上续：县初核落印即过，回到在核
        service.approve(6L, "县初核员", "可延续");
        assertBlock(r, NODE_CITY, MODE_AND, 2, 0, STATUS_RUNNING);
    }

    @Test
    public void rollbackFromCity_clearsLandedStampOfCurrentRound() {
        TSaltRenewBill r = file(7L, "XU-2026-007", NODE_CITY, MODE_AND, 2, 1, STATUS_RUNNING);
        service.rollback(7L, "退回县局");
        // 本档这一轮已落的一印当场清零，单子挪回县初核
        assertBlock(r, NODE_COUNTY, MODE_OR, 1, 0, STATUS_RETURNED);
    }

    @Test
    public void provinceApproval_blockedByUnreturnedOldCert() {
        danglingOldBills = 1; // 同号旧单尚未了结（在核/已挪回/待复议皆算未缴回）
        TSaltRenewBill r = file(8L, "XU-2026-008", NODE_PROVINCE, MODE_OR, 1, 0, STATUS_RUNNING);

        // 过往有未缴回旧证那一笔：这一票落不下去，单子原地不动
        assertNull(service.approve(8L, "省核准员", "可延续"));
        assertBlock(r, NODE_PROVINCE, MODE_OR, 1, 0, STATUS_RUNNING);

        // 旧证了结之后：看过，才落得下去
        danglingOldBills = 0;
        service.approve(8L, "省核准员", "可延续");
        assertBlock(r, NODE_PROVINCE, MODE_OR, 1, 1, STATUS_DONE);
    }

    @Test
    public void nullColumns_toleratedAndHealed() {
        // 存量空档：关号、算法、人数、情形全空，按县初核起算，落印后四栏摆平
        TSaltRenewBill r = file(9L, "XU-2026-009", null, null, null, null, null);
        TSaltRenewBill back = service.approve(9L, "县初核员", "可延续");
        assertSame(r, back);
        assertBlock(r, NODE_CITY, MODE_AND, 2, 0, STATUS_RUNNING);
    }

    @Test
    public void missingBill_orBlankApprover_refused() {
        assertNull(service.approve(404L, "县初核员", "可延续"));
        assertNull(service.reject(404L, "县初核员", "不可"));
        assertNull(service.rollback(404L, "退"));

        TSaltRenewBill r = file(10L, "XU-2026-010", NODE_COUNTY, MODE_OR, 1, 0, STATUS_RUNNING);
        assertNull(service.approve(10L, null, "可延续"));
        assertNull(service.approve(10L, "  ", "可延续"));
        assertBlock(r, NODE_COUNTY, MODE_OR, 1, 0, STATUS_RUNNING);
    }

    @Test
    public void deletedBill_refused() {
        TSaltRenewBill r = file(11L, "XU-2026-011", NODE_COUNTY, MODE_OR, 1, 0, STATUS_RUNNING);
        r.setDelFlag(1);
        assertNull(service.approve(11L, "县初核员", "可延续"));
        assertNull(service.reject(11L, "县初核员", "不可"));
        assertNull(service.rollback(11L, "退"));
        assertBlock(r, NODE_COUNTY, MODE_OR, 1, 0, STATUS_RUNNING);
    }

    @Test
    public void nodeMovesOneStepAtATime_neverSkips() {
        // 推进只许走紧邻的下一道：一票下去，关号至多挪一格
        TSaltRenewBill r = file(12L, "XU-2026-012", NODE_COUNTY, MODE_OR, 1, 0, STATUS_RUNNING);
        service.approve(12L, "县初核员", "可延续");
        assertEquals(Integer.valueOf(NODE_CITY), r.getNodeNo());
        // 市复审一票不够，仍旧停在市里这一档
        service.approve(12L, "市审查员甲", "可延续");
        assertEquals(Integer.valueOf(NODE_CITY), r.getNodeNo());
        assertEquals(Integer.valueOf(STATUS_RUNNING), r.getStatus());
    }

    @Test
    public void returnedBill_rewalksFromReturnedStage() {
        TSaltRenewBill r = file(13L, "XU-2026-013", NODE_CITY, MODE_AND, 2, 0, STATUS_RETURNED);
        // 挪回之后重走：第一票落下即回到在核
        service.approve(13L, "市审查员甲", "可延续");
        assertBlock(r, NODE_CITY, MODE_AND, 2, 1, STATUS_RUNNING);
        service.approve(13L, "市审查员乙", "可延续");
        assertBlock(r, NODE_PROVINCE, MODE_OR, 1, 0, STATUS_RUNNING);
    }
}
