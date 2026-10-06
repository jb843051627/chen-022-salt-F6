package com.fc.v2.controller.admin;

import java.util.HashMap;
import java.util.Map;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TSaltRenewBill;
import com.fc.v2.service.ITSaltRenewBillService;
import com.fc.v2.service.renew.RenewActionResult;
import com.fc.v2.service.renew.RenewRuleException;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

/**
 * 延续换证核签单 Controller（approval-chain 形状：三级核签流转入口）。
 *
 * <p>本处只收单转服务：压在哪一关、让不让走、两名审查员分歧停何处、
 * 省局点齐之后添不添得进印，说法全出自 RenewRules 一处，本处不另作答。
 *
 * @author fuce
 * @date 2026-10-05
 */
@Api(value = "延续换证核签单")
@Controller
@RequestMapping("/saltRenewBill")
public class SaltRenewBillController extends BaseController {

    /**
     * 唯一裁决处挡下的动作：把裁决处的原话原样回给屏上，不经全局处理器改写。
     */
    @ExceptionHandler(RenewRuleException.class)
    @ResponseBody
    public AjaxResult handleRule(RenewRuleException e) {
        return AjaxResult.error(e.getMessage());
    }

    private final String prefix = "admin/saltRenewBill";

    @Autowired
    private ITSaltRenewBillService saltRenewBillService;

    @ApiOperation(value = "核签台账跳转", notes = "核签台账跳转")
    @GetMapping("/view")
    @RequiresPermissions("saltRenewBill:view")
    public String view(ModelMap model) {
        return prefix + "/list";
    }

    @Log(title = "延续换证核签单台账", action = "list")
    @ApiOperation(value = "核签台账", notes = "核签台账")
    @GetMapping("/list")
    @RequiresPermissions("saltRenewBill:list")
    @ResponseBody
    public ResultTable list(TSaltRenewBill query) {
        QueryWrapper<TSaltRenewBill> queryWrapper = new QueryWrapper<TSaltRenewBill>()
                .eq("del_flag", 0)
                .orderByDesc("id");
        startPage();
        com.github.pagehelper.PageInfo<TSaltRenewBill> page =
                new com.github.pagehelper.PageInfo<TSaltRenewBill>(
                        saltRenewBillService.selectTSaltRenewBillList(queryWrapper));
        return pageTable(page.getList(), page.getTotal());
    }

    @Log(title = "延续换证核签单立案", action = "open")
    @ApiOperation(value = "立案", notes = "新开一张核签单压在县初核，在办张数加一")
    @PostMapping("/open")
    @RequiresPermissions("saltRenewBill:open")
    @ResponseBody
    public AjaxResult open(String billNo, String entNo) {
        TSaltRenewBill bill = saltRenewBillService.openBill(billNo, entNo);
        return AjaxResult.successData(200, bill);
    }

    @Log(title = "延续换证落印", action = "sign")
    @ApiOperation(value = "落一笔记", notes = "nodeNo 为递单带来的关号，只当来意，与单据所压档口不符时以单据为准")
    @PostMapping("/sign")
    @RequiresPermissions("saltRenewBill:sign")
    @ResponseBody
    public AjaxResult sign(Long id, Integer nodeNo, String signer, Integer verdict, String remark) {
        RenewActionResult result = saltRenewBillService.sign(id, nodeNo, signer, verdict, remark);
        Map<String, Object> data = new HashMap<>();
        data.put("outcome", result.getOutcome());
        data.put("reason", result.getReason());
        data.put("bill", result.getBill());
        return AjaxResult.successData(200, data);
    }

    @Log(title = "旧证缴回核查", action = "check")
    @ApiOperation(value = "省核准前登记旧证缴回核查", notes = "看过过往有无未缴回旧证那一笔")
    @PostMapping("/check")
    @RequiresPermissions("saltRenewBill:check")
    @ResponseBody
    public AjaxResult check(Long id, String checker, Integer oldCertReturned, String remark) {
        return AjaxResult.successData(200,
                saltRenewBillService.recordCheck(id, checker, oldCertReturned, remark));
    }

    @Log(title = "延续换证核签单退回", action = "rollback")
    @ApiOperation(value = "送回紧邻上一档", notes = "本档本轮落印当场清零留底，旧档旧印不动，轮次加一续走")
    @PostMapping("/rollback")
    @RequiresPermissions("saltRenewBill:rollback")
    @ResponseBody
    public AjaxResult rollback(Long id, String remark) {
        RenewActionResult result = saltRenewBillService.rollback(id, remark);
        Map<String, Object> data = new HashMap<>();
        data.put("outcome", result.getOutcome());
        data.put("reason", result.getReason());
        data.put("bill", result.getBill());
        return AjaxResult.successData(200, data);
    }

    @ApiOperation(value = "在办桌面", notes = "在办张数与逐档点数同一次清点；各关停留、办结/否决一并给")
    @GetMapping("/board")
    @RequiresPermissions("saltRenewBill:list")
    @ResponseBody
    public AjaxResult board() {
        return AjaxResult.successData(200, saltRenewBillService.board());
    }

    @ApiOperation(value = "逐档翻查", notes = "自省局往回收数到县初核，一块一块清点，新旧各轮分开摆")
    @GetMapping("/audit")
    @RequiresPermissions("saltRenewBill:list")
    @ResponseBody
    public AjaxResult audit(Long id) {
        return AjaxResult.successData(200, saltRenewBillService.audit(id));
    }

    @ApiOperation(value = "受理簿", notes = "顺落印次序的全部底档，含退回清零的旧笔")
    @GetMapping("/ledger")
    @RequiresPermissions("saltRenewBill:list")
    @ResponseBody
    public AjaxResult ledger(Long id) {
        return AjaxResult.successData(200, saltRenewBillService.ledger(id));
    }

    @ApiOperation(value = "发证名册只读", notes = "省局点齐同事务登记，此处不设手填入口")
    @GetMapping("/registry")
    @RequiresPermissions("saltRenewBill:list")
    @ResponseBody
    public AjaxResult registry() {
        return AjaxResult.successData(200, saltRenewBillService.listRegistry());
    }
}
