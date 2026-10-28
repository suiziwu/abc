# 人才培养模块 · 技能类别改造（路径二）

- 日期：2026-10-28
- 目标：把「按岗位方向整体打一个类别」改成「按技能逐个分类」，解决非 IT 方向环图只有一块、排行筛选为空的问题
- 结果：生产制造从 1 类（业务技能 191 条）变为 4 类；医疗护理 4 类；电商/新媒体 6 类；IT 方向的 4 类保持不变

## 一、改了什么

### 1. 新增技能字典表

`sql/talent_skill_category.sql`：

```sql
CREATE TABLE talent_skill_category (
  skill_name varchar(100) NOT NULL,
  category   varchar(50)  NOT NULL,
  update_time datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (skill_name)
);
```

- 已灌入当前库全部 **680 个技能**，覆盖 **48 个类别**
- IT 技能沿用历史数据中已有的逐技能类别（后端 / 前端 / AI大模型 / 工程化运维）
- 业务技能按技能名归入对应类别，例如：
  - 生产制造：生产管理（产线操作/装配作业/焊接工艺/车间管理）、质量管控（质量检验）、设备维护、安全规范（安全生产/安全管理）
  - 医疗护理：临床护理、医患沟通、药学服务、院感管理
  - 电商/新媒体：电商运营、内容创作、直播运营、客户服务、数据分析

### 2. 导入改为逐技能归类

新增 `SkillCategoryResolver`（`com.ruoyi.project.talent.service`）：

```
talent_skill_category 字典 → 关键词规则兜底 → 岗位方向默认类别
```

`TalentCsvImportService` 原来：

```java
String category = FAMILY_CATEGORY.getOrDefault(posting.getJobFamily(), "业务技能");
for (String skill : skills) insertSkill(..., category);   // 一个岗位所有技能同类别
```

现在：

```java
for (String skill : skills) {
    String category = skillCategoryResolver.resolve(skill, posting.getJobFamily());
    insertSkill(..., category);                            // 每个技能各自类别
}
```

新增技能不需要改代码：在 `talent_skill_category` 插一行即可；未登记的技能由关键词规则（质量/设备/护理/运营…）兜底。

### 3. 存量数据回填

```sql
UPDATE talent_job_skill s
JOIN talent_skill_category d ON d.skill_name = s.skill_name
SET s.skill_category = d.category;
```

71340 条技能全部按字典重算，快照与历史批次都生效。

### 4. 前端筛选改为动态

- 「技能需求排行」右上角的类别按钮不再写死 5 个，改为按当前方向的 `category-stat` 动态生成；切换方向时自动重置筛选
- 「技能类别分布」环图：类别超过 8 个时只画前 8 个，其余合并为「其他类别」，避免全部方向时图例过长

## 二、验证结果

接口（快照口径）：

| 方向 | 类别分布 |
|---|---|
| 生产制造 | 生产管理 86、设备维护 44、质量管控 44、安全规范 17 |
| 医疗护理 | 临床护理 55、医患沟通 28、药学服务 6、院感管理 4 |
| 电商/新媒体运营 | 内容创作 55、电商运营 48、客户服务 45、直播运营 32、数据分析 5、其他服务 3 |
| 全栈/AI应用开发 | AI大模型 26750、后端 17777、工程化运维 7172、前端 6813（保持不变） |

排行筛选：`/skill-rank?jobFamily=生产制造&category=质量管控` →

```
质量检验 44
```

导入链路：导入一条“生产操作工”岗位（描述含产线操作/装配作业/质量检验/设备维护），入库结果：

```
装配作业 → 生产管理
产线操作 → 生产管理
质量检验 → 质量管控
设备维护 → 设备维护
```

页面（headless 浏览器实测）：选中「生产制造」后，环图为 4 块，排行筛选按钮为 全部 / 生产管理 / 设备维护 / 质量管控 / 安全规范，控制台 0 错误。截图见 `talent_category_check.png`。

## 三、维护方式

- **新增技能**：往 `talent_skill_category` 插一行 `(技能名, 类别)`；服务重启后生效（字典在首次解析时加载并缓存）。
- **调整已有技能类别**：更新该表并重启；或在代码中调用 `SkillCategoryResolver.refresh()`。
- **新增方向**：在 `TalentCsvImportService.SKILL_RULES` 补该方向的技能抽取规则，并把技能登记进字典。
- **类别口径**：当前 48 类；前端全部方向时只展示前 8 类 + 其他，单方向展示该方向全部类别。

## 四、涉及文件

| 文件 | 改动 |
|---|---|
| `sql/talent_skill_category.sql` | 新增：建表 + 680 条字典数据 |
| `ruoyi-project/.../talent/service/SkillCategoryResolver.java` | 新增：技能→类别解析（字典/规则/方向兜底） |
| `ruoyi-project/.../talent/service/TalentCsvImportService.java` | 导入改为逐技能归类 |
| `ruoyi-project/.../talent/mapper/TalentMapper.java` | 新增 `selectSkillCategoryDict` |
| `ruoyi-project/src/main/resources/mapper/project/TalentMapper.xml` | 对应查询 |
| `ruoyi-ui/src/views/project/talent/index.vue` | 动态类别按钮、环图 Top8+其他、切方向重置筛选 |

> 说明：`FAMILY_CATEGORY` 静态映射已不再参与导入（保留未删，避免影响其他引用）；真正生效的是字典 + 规则。
