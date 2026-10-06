package com.fc.v2.model.auto;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.util.Date;

/**
 * 延续换证落印流水对象 t_salt_renew_sign
 *
 * @author fuce
 * @date 2026-10-05
 */
@TableName("t_salt_renew_sign")
@ApiModel(value = "TSaltRenewSign", description = "延续换证落印流水")
public class TSaltRenewSign implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 所属核签单主键 */
    @TableField("bill_id")
    @ApiModelProperty(value = "所属核签单主键")
    private Long billId;

    /** 所属核签单代号（受理簿编号） */
    @TableField("bill_no")
    @ApiModelProperty(value = "所属核签单代号（受理簿编号）")
    private String billNo;

    /** 落印当轮轮次 */
    @TableField("round_no")
    @ApiModelProperty(value = "落印当轮轮次")
    private Integer roundNo;

    /** 落印档口 0县初核 1市复审 2省核准 */
    @TableField("node_no")
    @ApiModelProperty(value = "落印档口 0县初核 1市复审 2省核准")
    private Integer nodeNo;

    /** 落印人 */
    @TableField("signer")
    @ApiModelProperty(value = "落印人")
    private String signer;

    /** 判词 1可延续 0不予延续 */
    @TableField("verdict")
    @ApiModelProperty(value = "判词 1可延续 0不予延续")
    private Integer verdict;

    /** 此笔还算不算数 0有效 1本轮退回当场清零（留底） */
    @TableField("valid")
    @ApiModelProperty(value = "此笔还算不算数 0有效 1本轮退回当场清零（留底）")
    private Integer valid;

    /** 省核准落印所凭的旧证核查笔主键 */
    @TableField("check_id")
    @ApiModelProperty(value = "省核准落印所凭的旧证核查笔主键")
    private Long checkId;

    /** 删除标记 0正常 1删除 */
    @TableField("del_flag")
    @ApiModelProperty(value = "删除标记 0正常 1删除")
    private Integer delFlag;

    /** 创建者 */
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建者")
    private String createBy;

    /** 创建时间（哪一天补的，各有底档） */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "创建时间")
    private Date createTime;

    /** 更新者 */
    @TableField(value = "update_by", fill = FieldFill.UPDATE)
    @ApiModelProperty(value = "更新者")
    private String updateBy;

    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "更新时间")
    private Date updateTime;

    /** 备注 */
    @TableField("remark")
    @ApiModelProperty(value = "备注")
    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getBillId() {
        return billId;
    }

    public void setBillId(Long billId) {
        this.billId = billId;
    }

    public String getBillNo() {
        return billNo;
    }

    public void setBillNo(String billNo) {
        this.billNo = billNo;
    }

    public Integer getRoundNo() {
        return roundNo;
    }

    public void setRoundNo(Integer roundNo) {
        this.roundNo = roundNo;
    }

    public Integer getNodeNo() {
        return nodeNo;
    }

    public void setNodeNo(Integer nodeNo) {
        this.nodeNo = nodeNo;
    }

    public String getSigner() {
        return signer;
    }

    public void setSigner(String signer) {
        this.signer = signer;
    }

    public Integer getVerdict() {
        return verdict;
    }

    public void setVerdict(Integer verdict) {
        this.verdict = verdict;
    }

    public Integer getValid() {
        return valid;
    }

    public void setValid(Integer valid) {
        this.valid = valid;
    }

    public Long getCheckId() {
        return checkId;
    }

    public void setCheckId(Long checkId) {
        this.checkId = checkId;
    }

    public Integer getDelFlag() {
        return delFlag;
    }

    public void setDelFlag(Integer delFlag) {
        this.delFlag = delFlag;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
