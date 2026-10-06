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
 * 旧证缴回核查笔对象 t_salt_renew_check
 *
 * @author fuce
 * @date 2026-10-05
 */
@TableName("t_salt_renew_check")
@ApiModel(value = "TSaltRenewCheck", description = "旧证缴回核查笔")
public class TSaltRenewCheck implements Serializable {
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

    /** 所属核签单代号 */
    @TableField("bill_no")
    @ApiModelProperty(value = "所属核签单代号")
    private String billNo;

    /** 核查当轮轮次（退回重走后须在新一轮重新看过） */
    @TableField("round_no")
    @ApiModelProperty(value = "核查当轮轮次")
    private Integer roundNo;

    /** 旧证缴回情形 1已缴回 0尚有未缴回旧证 */
    @TableField("old_cert_returned")
    @ApiModelProperty(value = "旧证缴回情形 1已缴回 0尚有未缴回旧证")
    private Integer oldCertReturned;

    /** 核查人 */
    @TableField("checker")
    @ApiModelProperty(value = "核查人")
    private String checker;

    /** 看过这一笔的时刻 */
    @TableField("check_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "看过这一笔的时刻")
    private Date checkTime;

    /** 删除标记 0正常 1删除 */
    @TableField("del_flag")
    @ApiModelProperty(value = "删除标记 0正常 1删除")
    private Integer delFlag;

    /** 创建者 */
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建者")
    private String createBy;

    /** 创建时间 */
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

    public Integer getOldCertReturned() {
        return oldCertReturned;
    }

    public void setOldCertReturned(Integer oldCertReturned) {
        this.oldCertReturned = oldCertReturned;
    }

    public String getChecker() {
        return checker;
    }

    public void setChecker(String checker) {
        this.checker = checker;
    }

    public Date getCheckTime() {
        return checkTime;
    }

    public void setCheckTime(Date checkTime) {
        this.checkTime = checkTime;
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
