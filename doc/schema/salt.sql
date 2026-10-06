-- salt 食盐专营管理与食盐质量安全监管 -- schema (chen-022)
-- 列名与基线实体契约（@TableName/@TableField）逐列对齐，改列必须同步实体。
-- 库：chen_022

CREATE TABLE IF NOT EXISTS t_salt_ent (
  id bigint NOT NULL COMMENT '主键',
  site_no varchar(64) DEFAULT NULL COMMENT '食盐定点企业代号',
  site_name varchar(128) DEFAULT NULL COMMENT '企业全称',
  site_type varchar(32) DEFAULT NULL COMMENT '企业类别',
  road_name varchar(128) DEFAULT NULL COMMENT '属地那一路(省—市—县)',
  status int DEFAULT NULL COMMENT '名录情形 0在册 1已摘牌',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='食盐定点企业总名录';

CREATE TABLE IF NOT EXISTS t_salt_iod_line (
  id bigint NOT NULL COMMENT '主键',
  rule_code varchar(64) DEFAULT NULL COMMENT '碘含量分档判定线代号',
  rule_name varchar(128) DEFAULT NULL COMMENT '线名',
  th1_max decimal(12,3) DEFAULT NULL COMMENT '起算数值(毫克每千克)',
  th2_max decimal(12,3) DEFAULT NULL COMMENT '够线数值(毫克每千克)',
  th3_max decimal(12,3) DEFAULT NULL COMMENT '封顶数值(毫克每千克)',
  eff_start datetime DEFAULT NULL COMMENT '立线之日',
  eff_end datetime DEFAULT NULL COMMENT '让位之日(不含)',
  priority int DEFAULT NULL COMMENT '让位顺位(数值大的先说话)',
  status int DEFAULT NULL COMMENT '线的情形 0在场 1已停用',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='碘含量分档判定线';

CREATE TABLE IF NOT EXISTS t_salt_link_page (
  id bigint NOT NULL COMMENT '主键',
  bill_no varchar(64) DEFAULT NULL COMMENT '企业品种挂接代号',
  node_no int DEFAULT NULL COMMENT '适用区域层级序',
  site_id int DEFAULT NULL COMMENT '所属定点企业',
  site_no varchar(64) DEFAULT NULL COMMENT '所属企业代号',
  should_count int DEFAULT NULL COMMENT '应挂品种数',
  done_count int DEFAULT NULL COMMENT '已挂品种数',
  lack_count int DEFAULT NULL COMMENT '欠挂品种数',
  content varchar(255) DEFAULT NULL COMMENT '随页交来的标签要件',
  status int DEFAULT NULL COMMENT '挂接进展 0待挂 1已挂讫 2压页',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='企业品种挂接簿页';

CREATE TABLE IF NOT EXISTS t_salt_record_flow (
  id bigint NOT NULL COMMENT '主键',
  biz_no varchar(64) DEFAULT NULL COMMENT '跨省经营备案单代号',
  stage int DEFAULT NULL COMMENT '当前步次 0..3（递单受理/材料核验/生效确认/缴回封卷）',
  status int DEFAULT NULL COMMENT '备案单走到的那一步 0未起 1在办 2已封卷',
  content varchar(255) DEFAULT NULL COMMENT '一段一记',
  last_action varchar(64) DEFAULT NULL COMMENT '最近一次挪步动作',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='跨省经营备案单';

CREATE TABLE IF NOT EXISTS t_salt_renew_bill (
  id bigint NOT NULL COMMENT '主键',
  bill_no varchar(64) DEFAULT NULL COMMENT '延续换证核签单',
  ent_no varchar(64) DEFAULT NULL COMMENT '所对批发企业代号（受理对象）',
  node_no int DEFAULT NULL COMMENT '当前所在档口 0..2（县初核/市复审/省核准）',
  cur_round int DEFAULT '1' COMMENT '当前轮次：立案为1，每退回一记加一，新旧各轮分开留底',
  sign_mode int DEFAULT NULL COMMENT '本档算齐办法 0一名即可 1两名须同判 2一名且须先查旧证',
  need_count int DEFAULT NULL COMMENT '本档应落人数（系统按档口填，不留人手格子）',
  sign_count int DEFAULT NULL COMMENT '本档已落印数（系统顺落印次序逐笔点出回填，不许手填）',
  status int DEFAULT NULL COMMENT '核签情形 0在核 1已办结 2已否决',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除（办结单不得删除）',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_renew_bill_no (bill_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='延续换证核签单';

-- 落印流水：核签单上每一笔印都在此成行；退回只作软清零（valid 置 1），旧笔一笔不抹。
CREATE TABLE IF NOT EXISTS t_salt_renew_sign (
  id bigint NOT NULL COMMENT '主键',
  bill_id bigint NOT NULL COMMENT '所属核签单主键',
  bill_no varchar(64) DEFAULT NULL COMMENT '所属核签单代号（受理簿编号）',
  round_no int NOT NULL COMMENT '落印当轮轮次',
  node_no int NOT NULL COMMENT '落印档口 0县初核 1市复审 2省核准',
  signer varchar(64) NOT NULL COMMENT '落印人',
  verdict int NOT NULL COMMENT '判词 1可延续 0不予延续',
  valid int DEFAULT '0' COMMENT '此笔还算不算数 0有效 1本轮退回当场清零（留底）',
  check_id bigint DEFAULT NULL COMMENT '省核准落印所凭的旧证核查笔主键',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间（哪一天补的，各有底档）',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_renew_sign_bill (bill_id, round_no, node_no, valid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='延续换证落印流水';

-- 旧证缴回核查：省核准落印前必有的那一笔，看过过往有无未缴回旧证。
CREATE TABLE IF NOT EXISTS t_salt_renew_check (
  id bigint NOT NULL COMMENT '主键',
  bill_id bigint NOT NULL COMMENT '所属核签单主键',
  bill_no varchar(64) DEFAULT NULL COMMENT '所属核签单代号',
  round_no int NOT NULL COMMENT '核查当轮轮次（退回重走后须在新一轮重新看过）',
  old_cert_returned int NOT NULL COMMENT '旧证缴回情形 1已缴回 0尚有未缴回旧证',
  checker varchar(64) NOT NULL COMMENT '核查人',
  check_time datetime NOT NULL COMMENT '看过这一笔的时刻',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_renew_check_bill (bill_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='旧证缴回核查笔';

-- 发证名册：省局点齐的同一事务里登记，核签结果与名册同一次算出，不许一处取数一处手填。
CREATE TABLE IF NOT EXISTS t_salt_cert_registry (
  id bigint NOT NULL COMMENT '主键',
  bill_no varchar(64) NOT NULL COMMENT '来源核签单代号',
  ent_no varchar(64) DEFAULT NULL COMMENT '领证批发企业代号',
  issue_no varchar(64) DEFAULT NULL COMMENT '新发证书编号',
  issue_time datetime DEFAULT NULL COMMENT '发证时刻（与省局落印同时同事务）',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除（办结名册不得删除）',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_cert_registry_bill (bill_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='批发企业发证名册';

CREATE TABLE IF NOT EXISTS t_salt_spot_row (
  id bigint NOT NULL COMMENT '主键',
  batch_no varchar(64) DEFAULT NULL COMMENT '监督抽检结果报送册码',
  row_no int DEFAULT NULL COMMENT '原报行次',
  item_code varchar(64) DEFAULT NULL COMMENT '被对上的定点企业代号',
  qty decimal(12,2) DEFAULT NULL COMMENT '本行不合格件数',
  hours_num decimal(12,2) DEFAULT NULL COMMENT '本行送检件数',
  status int DEFAULT NULL COMMENT '行落地情形 0未勾 1已收下 2已退回',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='抽检结果报送核收行';

CREATE TABLE IF NOT EXISTS t_salt_year_task (
  id bigint NOT NULL COMMENT '主键',
  item_no varchar(64) DEFAULT NULL COMMENT '年检催办条目代号',
  due_at datetime DEFAULT NULL COMMENT '应开口那一日的止点时刻',
  amount decimal(12,2) DEFAULT NULL COMMENT '最可提前几日开口',
  content varchar(255) DEFAULT NULL COMMENT '事由与所对企业的记要',
  status int DEFAULT NULL COMMENT '条目情形 0待派 1已派出 2分不出',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='年检到期催办单';

-- 初始档案数据（状态约定：id=1 启用 / id=2 停用）
-- t_salt_ent 预置两条种子名录：id=0 在册、id=1 已摘牌；企业代号与类别按各表既有排法给值。
INSERT IGNORE INTO t_salt_ent (id, site_no, site_name, site_type, road_name, status, del_flag, create_by, create_time)
VALUES (0, 'JY00', '盐泽制盐一厂（制盐企业，在册）', '制盐企业', '盐岭省—盐泽市—临卤县', 0, 0, 'seed', NOW()),
       (1, 'JY01', '青卤批发部（转行后摘牌）', '省内批发企业', '盐岭省—盐泽市—青卤县', 1, 0, 'seed', NOW());

