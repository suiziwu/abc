package com.ruoyi.project.talent.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PushbackReader;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.ruoyi.project.talent.domain.TalentJobPosting;
import com.ruoyi.project.talent.mapper.TalentMapper;

/**
 * 人才培养模块：CSV 岗位数据导入。
 *
 * <p>支持逐行业抓取结果（行业编码/行业名称/层级/上级行业/职位名称/职位描述），
 * 也兼容仅含职位名称与职位描述的通用 CSV。导入后自动归类岗位方向、行业并提取技能标签。</p>
 *
 * @author ruoyi
 */
@Service
public class TalentCsvImportService
{
    private static final Logger log = LoggerFactory.getLogger(TalentCsvImportService.class);

    private static final String OTHER_FAMILY = "其他";

    /** 顺序敏感：先具体岗位，再行业兜底 */
    private static final Object[][] FAMILY_RULES = {
            { Pattern.compile("财务|会计|出纳|税务|财务BP"), "财务/人力/行政" },
            { Pattern.compile("医生|医师|药师|药剂|药房|护理|护士|检验|康复|彩超|口腔|儿科|中医|验光|视光|推拿"), "医疗护理" },
            { Pattern.compile("教师|讲师|助教|数学|老师|围棋|托育|保育|辅导老师|辅导教师|培训师"), "教育培训" },
            { Pattern.compile("游戏陪玩|游戏主播|语音厅|场控|主播|陪练|普通话|配音"), "电商/新媒体运营" },
            { Pattern.compile("短视频|剪辑|拍摄|编导|直播|电商运营|店铺运营|京东.*运营|淘宝.*运营|外卖平台运营"), "电商/新媒体运营" },
            { Pattern.compile("电商.*采购|采购/供应链|供应链产品"), "电商/新媒体运营" },
            { Pattern.compile("摄影|婚庆|婚恋|婚礼"), "旅游/婚庆服务" },
            { Pattern.compile("旅游|导游|会奖|同业销售"), "旅游/婚庆服务" },
            { Pattern.compile("保洁|家政|打扫|家电清洗"), "家政服务" },
            { Pattern.compile("洗车|洗美|汽车美容|汽车维修|汽修|4S店"), "其他" },
            { Pattern.compile("美容|美睫|美甲|化妆|皮肤管理"), "美容美发" },
            { Pattern.compile("发型|美发|造型师"), "美容美发" },
            { Pattern.compile("健身|私教|教练|SPA|按摩|养生|技师|理疗"), "健康/健身/养生" },
            { Pattern.compile("厨师|后厨|服务员|传菜|餐饮|收银|店员|肯德基|海底捞|管培生|酒店|客房|前厅"), "餐饮/酒店服务" },
            { Pattern.compile("弱电|布线|手机维修|嵌入式|通信|电子工程|IC设计|芯片设计|PCB"), "电子/通信技术" },
            { Pattern.compile("装配|操作工|普工|产线|质检|焊接|学生工|长白班|车间|厂长|生产主管|安全员|消防中控"), "生产制造" },
            { Pattern.compile("快递|打包|分拣|物流|配送|库管|仓库|装卸|搬运|理货|跟单"), "物流/仓储" },
            { Pattern.compile("结构设计|结构工程师|工业设计|机械设计|模具设计|电机设计|消防设计|电路设计|模拟电路|施工图|绘图|建筑|室内设计|全屋定制|轮胎结构"), "产品/结构设计" },
            { Pattern.compile("UI|交互设计|视觉设计|平面设计|美工|设计师|动画设计|3D建模|建模师|展厅设计|包装设计"), "UI/视觉设计" },
            { Pattern.compile("销售|客户经理|大客户|BD|渠道|营销|拓展|营业员|会籍|顾问|客服|客诉|售后|业务员|业务专员"), "销售/商务" },
            { Pattern.compile("产品经理|产品专家|解决方案|运营"), "产品/运营" },
            { Pattern.compile("Java|Python|golang|全栈|开发工程师|后端|前端|算法|测试|运维|实施|IT支持|技术支持|技术专员|AI应用|AI开发|AI工程|AI全栈|AIGC|智能体|Agent"), "IT" },
            { Pattern.compile("招聘|人事|人力|文员|行政|总助|董事长助理|CEO助理|总经理助理"), "财务/人力/行政" },
    };

    private static final Object[][] IT_DETAIL_RULES = {
            { Pattern.compile("大数据|数据开发|数据分析|数据平台"), "数据开发" },
            { Pattern.compile("前端|Vue|React"), "前端开发" },
            { Pattern.compile("算法"), "算法工程师" },
            { Pattern.compile("测试"), "软件测试" },
            { Pattern.compile("运维|实施"), "运维/DevOps" },
            { Pattern.compile("嵌入式"), "C++/嵌入式开发" },
            { Pattern.compile("全栈"), "全栈/AI应用开发" },
            { Pattern.compile("产品|解决方案|运营"), "产品/运营" },
            { Pattern.compile("Java|Python|golang|Go语言|后端"), "后端开发" },
    };

    private static final Map<String, String> INDUSTRY_FALLBACK = new LinkedHashMap<>();
    private static final Map<String, Map<String, Pattern>> SKILL_RULES = new LinkedHashMap<>();
    private static final Map<String, String> FAMILY_CATEGORY = new LinkedHashMap<>();

    static
    {
        INDUSTRY_FALLBACK.put("互联网", "IT");
        INDUSTRY_FALLBACK.put("电子商务", "电商/新媒体运营");
        INDUSTRY_FALLBACK.put("计算机软件", "IT");
        INDUSTRY_FALLBACK.put("生活服务(O2O)", "餐饮/酒店服务");
        INDUSTRY_FALLBACK.put("企业服务", "餐饮/酒店服务");
        INDUSTRY_FALLBACK.put("医疗健康", "医疗护理");
        INDUSTRY_FALLBACK.put("游戏", "电商/新媒体运营");
        INDUSTRY_FALLBACK.put("社交网络与媒体", "电商/新媒体运营");
        INDUSTRY_FALLBACK.put("人工智能", "IT");
        INDUSTRY_FALLBACK.put("云计算", "IT");
        INDUSTRY_FALLBACK.put("在线教育", "教育培训");
        INDUSTRY_FALLBACK.put("计算机服务", "IT");
        INDUSTRY_FALLBACK.put("大数据", "IT");
        INDUSTRY_FALLBACK.put("广告营销", "电商/新媒体运营");
        INDUSTRY_FALLBACK.put("物联网", "销售/商务");
        INDUSTRY_FALLBACK.put("新零售", "销售/商务");
        INDUSTRY_FALLBACK.put("信息安全", "销售/商务");
        INDUSTRY_FALLBACK.put("半导体/芯片", "生产制造");
        INDUSTRY_FALLBACK.put("电子/硬件开发", "生产制造");
        INDUSTRY_FALLBACK.put("通信/网络设备", "电子/通信技术");
        INDUSTRY_FALLBACK.put("智能硬件/消费电子", "产品/结构设计");
        INDUSTRY_FALLBACK.put("运营商/增值服务", "物流/仓储");
        INDUSTRY_FALLBACK.put("计算机硬件", "财务/人力/行政");
        INDUSTRY_FALLBACK.put("餐饮", "餐饮/酒店服务");
        INDUSTRY_FALLBACK.put("美容", "美容美发");
        INDUSTRY_FALLBACK.put("美发", "美容美发");
        INDUSTRY_FALLBACK.put("酒店/民宿", "餐饮/酒店服务");
        INDUSTRY_FALLBACK.put("休闲/娱乐", "健康/健身/养生");
        INDUSTRY_FALLBACK.put("运动/健身", "健康/健身/养生");
        INDUSTRY_FALLBACK.put("保健/养生", "健康/健身/养生");
        INDUSTRY_FALLBACK.put("家政服务", "家政服务");
        INDUSTRY_FALLBACK.put("旅游/景区", "旅游/婚庆服务");
        INDUSTRY_FALLBACK.put("婚庆/摄影", "旅游/婚庆服务");

        addSkills("电商/新媒体运营",
                "短视频拍摄", "拍摄", "视频剪辑", "剪辑", "直播运营", "直播|主播|场控|陪玩",
                "电商平台运营", "电商|京东|淘宝|拼多多|店铺|外卖平台", "店铺推广", "推广|营销策略|流量",
                "内容策划", "编导|选题|内容策划|脚本", "客户沟通", "客服|互动|沟通",
                "跨境电商", "跨境|TikTok|海外|葡萄牙语", "游戏陪玩", "游戏陪玩|端游|LOL|CSGO",
                "数据分析", "数据分析|数据监控|分析数据|数据调整");
        addSkills("销售/商务",
                "客户开发", "客户开发|开拓|开发新客户|拓展客户|拓客", "商务谈判", "谈判|签约|商务",
                "渠道拓展", "渠道|经销商|合作资源", "大客户管理", "大客户|KA|客户关系",
                "销售管理", "销售计划|销售团队|销售指标|业绩", "客户关系维护", "维护|跟进",
                "招投标", "投标|招标|标书", "会籍销售", "会籍|会员",
                "数据分析", "数据分析|数据统计|报表分析",
                "客户服务", "客服|客诉|售后|投诉", "电话销售", "电话|外呼|电销");
        addSkills("餐饮/酒店服务",
                "顾客接待", "接待|门迎", "点餐服务", "点餐|上菜|传菜",
                "后厨备餐", "后厨|切配|食材|水台|洗碗", "食品安全", "食品安全|健康证|卫生",
                "门店运营", "门店|店面|餐厅运营|管培", "收银结算", "收银|结算",
                "客房服务", "客房|前厅|酒店", "成本控制", "成本|毛利|核算");
        addSkills("财务/人力/行政",
                "财务核算", "财务|会计|账务|凭证|报表", "税务申报", "税务|报税",
                "招聘管理", "招聘|寻访|面试", "人事事务", "人事|人力|员工关系|入职",
                "行政办公", "行政|文档|办公", "经营分析", "经营分析|预算|决策支持",
                "客户对接", "客户对接|对接客户", "财务系统", "ERP|财务系统|用友|金蝶");
        addSkills("美容美发",
                "皮肤护理", "皮肤|护理|面部", "美睫美甲", "美睫|美甲",
                "发型设计", "发型|染发|烫发|剪发", "化妆造型", "化妆|造型",
                "客户沟通", "客户|沟通", "门店销售", "销售|提成|业绩|门店",
                "门店运营", "门店|店面|店务");
        addSkills("旅游/婚庆服务",
                "旅游产品设计", "旅游产品|线路|定制", "客户接待", "接待|客户对接|咨询",
                "行程策划", "行程|策划|团建|游学", "婚礼策划", "婚礼|婚庆|婚恋|红娘",
                "摄影服务", "摄影|拍摄|婚纱", "销售推广", "销售|推广|同业",
                "会奖MICE", "会奖|MICE", "外语沟通", "英语|外语|葡萄牙语|双语");
        addSkills("医疗护理",
                "临床护理", "护理|临床|护士", "口腔护理", "口腔|牙科",
                "健康评估", "健康评估|体检|功能医学", "医患沟通", "沟通|咨询|客户",
                "院感管理", "院感|感染|医疗安全", "验光配镜", "验光|配镜|视力",
                "康复治疗", "康复|理疗|推拿", "药学服务", "药|药剂|中药",
                "医学检验", "检验|化验|病理", "儿童康复", "儿童|感统|康复");
        addSkills("生产制造",
                "装配作业", "装配|组装", "产线操作", "产线|操作工|机台|普工|学生工",
                "质量检验", "质检|质量", "设备维护", "设备|维护|调试",
                "安全生产", "安全|操作规范", "焊接工艺", "焊接",
                "车间管理", "车间|厂长|生产主管|班组", "安全管理", "安全员|消防|安全生产");
        addSkills("电子/通信技术",
                "弱电施工", "弱电|布线|施工", "嵌入式开发", "嵌入式|固件|STM32|ARM",
                "硬件维修", "维修|检测|手机", "通信设备调试", "通信|网络设备|移动",
                "电路调试", "电路|调试|电子", "电路设计", "电路设计|原理图|PCB");
        addSkills("家政服务",
                "家政保洁", "保洁|打扫|清洁", "家电清洗", "家电|清洗",
                "收纳整理", "收纳|整理", "客户沟通", "客户|沟通", "安全操作", "安全|健康证");
        addSkills("教育培训",
                "学科教学", "教学|教师|数学", "课程设计", "课程|教研",
                "课堂管理", "课堂|班级|管理", "家校沟通", "家长|沟通", "普通话", "普通话",
                "围棋教学", "围棋", "托育照护", "托育|保育", "学习辅导", "辅导|作业");
        addSkills("产品/结构设计",
                "结构设计", "结构", "3D建模", "3D|建模", "2D制图", "2D|图纸|制图",
                "产品规划", "产品规划|产品线", "需求分析", "需求|分析", "BOM管理", "BOM",
                "机械设计", "机械|模具|电机|消防|施工图|Revit|BIM", "电路设计", "电路|原理图|芯片");
        addSkills("物流/仓储",
                "快递分拣", "快递|分拣", "打包作业", "打包|理货",
                "仓储管理", "仓储|库存", "配送调度", "配送|调度|物流",
                "装卸搬运", "装卸|搬运", "库存盘点", "库管|仓库|盘点", "单证跟单", "跟单|单证");
        addSkills("健康/健身/养生",
                "健身指导", "健身|训练|器械", "私教课程", "私教|课程|训练计划",
                "会员管理", "会员|会籍", "按摩理疗", "按摩|理疗|SPA",
                "健康咨询", "健康|咨询", "营养配餐", "营养|配餐");
        addSkills("后端开发",
                "Java", "Java", "Spring Boot", "Spring Boot|SpringBoot", "MySQL", "MySQL",
                "Redis", "Redis", "微服务", "微服务|Spring Cloud", "高并发", "高并发|并发");
        addSkills("前端开发",
                "JavaScript", "JavaScript|JS", "Vue", "Vue", "React", "React",
                "TypeScript", "TypeScript|TS", "CSS", "CSS|HTML");
        addSkills("全栈/AI应用开发",
                "Java", "Java", "JavaScript", "JavaScript|JS", "Vue", "Vue",
                "React", "React", "Spring Boot", "Spring Boot|SpringBoot", "Node.js", "Node.js|Node");
        addSkills("算法工程师",
                "Python", "Python", "PyTorch", "PyTorch", "TensorFlow", "TensorFlow",
                "机器学习", "机器学习", "深度学习", "深度学习", "大模型", "大模型|LLM");
        addSkills("数据开发",
                "Python", "Python", "SQL", "SQL", "Spark", "Spark", "Flink", "Flink",
                "数据仓库", "数据仓库|数仓", "大数据", "大数据|Hadoop|Hive");
        addSkills("软件测试",
                "自动化测试", "自动化测试", "测试用例", "测试用例", "单元测试", "单元测试",
                "接口测试", "接口测试|Postman", "性能测试", "性能测试|JMeter");
        addSkills("运维/DevOps",
                "Linux", "Linux", "Docker", "Docker", "Kubernetes", "Kubernetes|K8s",
                "CI/CD", "CI/CD|Jenkins", "监控", "监控|Prometheus");
        addSkills("C++/嵌入式开发",
                "C++", "C\\+\\+", "嵌入式", "嵌入式", "STM32", "STM32", "Linux", "Linux");
        addSkills("产品/运营",
                "需求分析", "需求分析|需求调研", "产品规划", "产品规划|产品设计",
                "数据分析", "数据分析", "用户增长", "用户增长|用户运营",
                "内容运营", "内容|文案|选题|新媒体", "活动策划", "活动|策划|落地",
                "用户运营", "用户|社群|留存", "数据复盘", "复盘|数据|分析");
        addSkills("UI/视觉设计",
                "UI设计", "UI设计|UI", "交互设计", "交互设计", "Figma", "Figma|Sketch", "视觉设计", "视觉设计",
                "平面设计", "平面|海报|广告设计|图文", "3D建模", "3D|建模|建模师", "动画设计", "动画|AE|Flash", "美工", "美工");
        addSkills("其他",
                "金融产品知识", "证券|基金|股票|期货|理财|投资|交易|金融|保险|银行",
                "风险合规", "风控|合规|风险", "外语沟通", "英语|翻译|日语|韩语|法语|西班牙语",
                "数据分析", "数据分析|数据统计|数据整理|报表",
                "客户沟通", "客户|沟通|服务|对接", "项目管理", "项目|协调|统筹|推进",
                "办公软件", "办公软件|Excel|Word|PPT|Office|WPS", "公文写作", "公文|政策|报告|材料|文稿",
                "农业技术", "养殖|饲养|种植|农业|猪|羊");

        FAMILY_CATEGORY.put("后端开发", "后端");
        FAMILY_CATEGORY.put("前端开发", "前端");
        FAMILY_CATEGORY.put("全栈/AI应用开发", "后端");
        FAMILY_CATEGORY.put("算法工程师", "AI大模型");
        FAMILY_CATEGORY.put("数据开发", "后端");
        FAMILY_CATEGORY.put("软件测试", "工程化运维");
        FAMILY_CATEGORY.put("运维/DevOps", "工程化运维");
        FAMILY_CATEGORY.put("C++/嵌入式开发", "后端");
        FAMILY_CATEGORY.put("产品/运营", "业务技能");
        FAMILY_CATEGORY.put("UI/视觉设计", "前端");
    }

    @Autowired
    private TalentMapper talentMapper;

    @Autowired
    private SkillCategoryResolver skillCategoryResolver;

    private static void addSkills(String family, String... pairs)
    {
        Map<String, Pattern> rules = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2)
        {
            rules.put(pairs[i], Pattern.compile(pairs[i + 1]));
        }
        SKILL_RULES.put(family, rules);
    }

    /**
     * 导入 CSV 岗位数据。
     *
     * @param file      CSV 文件（UTF-8）
     * @param batchDate 采集批次，留空表示纳入当前快照
     * @return 导入结果摘要
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> importCsv(MultipartFile file, String batchDate) throws IOException
    {
        if (file == null || file.isEmpty())
        {
            throw new IllegalArgumentException("请选择要导入的 CSV 文件");
        }
        String fileName = file.getOriginalFilename() == null ? "upload.csv" : file.getOriginalFilename();
        String source = buildSource(fileName);
        String batch = batchDate == null ? "" : batchDate.trim();

        byte[] bytes = file.getBytes();
        int offset = 0;
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF)
        {
            offset = 3;   // 跳过 UTF-8 BOM，避免表头首个字段带 \uFEFF
        }
        Charset charset = detectCharset(bytes, offset);
        List<List<String>> rows = parseCsv(new InputStreamReader(
                new ByteArrayInputStream(bytes, offset, bytes.length - offset), charset));
        if (rows.size() < 2)
        {
            throw new IllegalArgumentException("CSV 内容为空或缺少表头");
        }

        Map<String, Integer> header = mapHeader(rows.get(0));
        Integer titleIdx = pick(header, "职位名称", "岗位名称", "job_title", "jobtitle", "title");
        Integer descIdx = pick(header, "职位描述", "岗位描述", "description", "job_desc", "jobdesc");
        if (titleIdx == null)
        {
            throw new IllegalArgumentException("CSV 缺少「职位名称」列");
        }
        Integer levelIdx = pick(header, "层级", "level");
        Integer industry2Idx = pick(header, "行业名称", "二级行业", "industry", "industry2");
        Integer industry1Idx = pick(header, "上级行业", "一级行业", "parent_industry", "industry1");
        Integer cityIdx = pick(header, "城市", "city");
        Integer keywordIdx = pick(header, "关键词", "岗位关键词", "keyword");

        List<TalentJobPosting> postings = new ArrayList<>();
        List<List<String>> postingSkills = new ArrayList<>();
        int skipped = 0;

        for (int i = 1; i < rows.size(); i++)
        {
            List<String> cells = rows.get(i);
            if (isEmptyRow(cells))
            {
                continue;
            }
            String level = cell(cells, levelIdx);
            if (levelIdx != null && !level.isEmpty() && !"二级".equals(level))
            {
                skipped++;
                continue;
            }
            String title = clean(cell(cells, titleIdx));
            if (title.isEmpty() || "未知职位".equals(title))
            {
                skipped++;
                continue;
            }
            String desc = clean(cell(cells, descIdx));
            String industry2 = cell(cells, industry2Idx);
            String industry1 = cell(cells, industry1Idx);
            String industry = !industry1.isEmpty() ? industry1 : (!industry2.isEmpty() ? industry2 : "其他");
            String family = resolveFamily(title, desc, industry2);
            List<String> skills = extractSkills(title + " " + desc, family);
            String keyword = cell(cells, keywordIdx);

            TalentJobPosting posting = new TalentJobPosting();
            posting.setBatchDate(batch);
            posting.setKeyword(keyword.isEmpty() ? industry2 : keyword);
            posting.setCity(cell(cells, cityIdx).isEmpty() ? "青岛" : cell(cells, cityIdx));
            posting.setJobTitle(title);
            posting.setDescription(desc);
            posting.setSource(source);
            posting.setSkillNames(String.join(",", skills));
            posting.setCreateTime(new Date());
            posting.setJobFamily(family);
            posting.setIndustry(industry);

            postings.add(posting);
            postingSkills.add(skills);
        }

        if (postings.isEmpty())
        {
            throw new IllegalArgumentException("没有可导入的岗位行，请检查「职位名称」列是否为空");
        }

        // 只清理「同来源 + 同采集时间」的旧数据：同一文件换个采集时间重导会保留上一期，实现按时间累积
        talentMapper.deleteSkillsBySource(source, batch);
        talentMapper.deletePostingsBySource(source, batch);

        int skillCount = 0;
        Set<String> industries = new LinkedHashSet<>();
        Set<String> families = new LinkedHashSet<>();
        for (int i = 0; i < postings.size(); i++)
        {
            TalentJobPosting posting = postings.get(i);
            talentMapper.insertPosting(posting);
            industries.add(posting.getIndustry());
            families.add(posting.getJobFamily());
            for (String skill : postingSkills.get(i))
            {
                // 路径二：按技能查字典/规则得到真实类别，而不是整个方向共用一类
                String category = skillCategoryResolver.resolve(skill, posting.getJobFamily());
                talentMapper.insertSkill(posting.getId(), batch, skill, category);
                skillCount++;
            }
        }

        log.info("CSV 导入完成：来源={}，岗位={}，技能={}", source, postings.size(), skillCount);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("source", source);
        result.put("fileName", fileName);
        result.put("batchDate", batch);
        result.put("postingCount", postings.size());
        result.put("skillCount", skillCount);
        result.put("skipped", skipped);
        result.put("industryCount", industries.size());
        result.put("familyCount", families.size());
        result.put("industries", new ArrayList<>(industries));
        result.put("families", new ArrayList<>(families));
        return result;
    }

    /** talent_job_posting.source 的列宽 */
    private static final int SOURCE_MAX_LENGTH = 128;

    /**
     * 构造导入来源标记。
     *
     * <p>{@code source} 列长度有限，而上传文件名可能很长（浏览器会把完整文件名传上来）。
     * 以前直接拼「CSV导入:」+ 文件名，名字一长就抛
     * {@code Data too long for column 'source'}，整次导入失败、一条都进不去。
     * 这里超长时保留文件名头尾（尾巴通常是时间戳，更有辨识度），中间用省略号代替。</p>
     */
    static String buildSource(String fileName)
    {
        String prefix = "CSV导入:";
        String name = fileName == null ? "" : fileName;
        if (prefix.length() + name.length() <= SOURCE_MAX_LENGTH)
        {
            return prefix + name;
        }
        int keep = SOURCE_MAX_LENGTH - prefix.length() - 3;
        int head = keep / 2;
        int tail = keep - head;
        return prefix + name.substring(0, head) + "..." + name.substring(name.length() - tail);
    }

    static String resolveFamily(String title, String desc, String industry2)
    {
        String text = title + " " + desc;
        for (Object[] rule : FAMILY_RULES)
        {
            if (((Pattern) rule[0]).matcher(title).find())
            {
                String family = (String) rule[1];
                if ("IT".equals(family))
                {
                    return resolveItFamily(text);
                }
                return family;
            }
        }
        String fallback = INDUSTRY_FALLBACK.get(industry2);
        if (fallback == null)
        {
            return OTHER_FAMILY;
        }
        return "IT".equals(fallback) ? "全栈/AI应用开发" : fallback;
    }

    private static String resolveItFamily(String text)
    {
        for (Object[] rule : IT_DETAIL_RULES)
        {
            if (((Pattern) rule[0]).matcher(text).find())
            {
                return (String) rule[1];
            }
        }
        return "全栈/AI应用开发";
    }

    static List<String> extractSkills(String text, String family)
    {
        List<String> skills = new ArrayList<>();
        Map<String, Pattern> rules = SKILL_RULES.get(family);
        if (rules == null)
        {
            return skills;
        }
        for (Map.Entry<String, Pattern> entry : rules.entrySet())
        {
            if (entry.getValue().matcher(text).find() && !skills.contains(entry.getKey()))
            {
                skills.add(entry.getKey());
                if (skills.size() >= 4)
                {
                    break;
                }
            }
        }
        return skills;
    }

    static Map<String, Integer> mapHeader(List<String> header)
    {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < header.size(); i++)
        {
            String key = clean(header.get(i)).toLowerCase();
            if (!key.isEmpty())
            {
                map.put(key, i);
            }
        }
        return map;
    }

    static Integer pick(Map<String, Integer> header, String... names)
    {
        for (String name : names)
        {
            Integer idx = header.get(name.toLowerCase());
            if (idx != null)
            {
                return idx;
            }
        }
        return null;
    }

    static String cell(List<String> cells, Integer idx)
    {
        if (idx == null || idx >= cells.size())
        {
            return "";
        }
        return cells.get(idx) == null ? "" : cells.get(idx).trim();
    }

    private static boolean isEmptyRow(List<String> cells)
    {
        for (String cell : cells)
        {
            if (cell != null && !cell.trim().isEmpty())
            {
                return false;
            }
        }
        return true;
    }

    static String clean(String value)
    {
        if (value == null)
        {
            return "";
        }
        String result = value.replace("\uFEFF", "")
                .replaceAll("(BOSS直聘|boss|直聘|kanzhun|来自BOSS直聘)", "")
                .replaceAll("\\s+", " ")
                .trim();
        return result;
    }

    /**
     * 识别 CSV 编码：优先 UTF-8，解码失败时按 GBK（Excel 在中文 Windows 上默认导出 ANSI/GBK）。
     */
    static Charset detectCharset(byte[] bytes, int offset)
    {
        try
        {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, offset, bytes.length - offset));
            return StandardCharsets.UTF_8;
        }
        catch (CharacterCodingException e)
        {
            return Charset.forName("GBK");
        }
    }

    /** 支持引号包裹、字段内逗号与换行的 CSV 解析 */
    static List<List<String>> parseCsv(Reader reader) throws IOException
    {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        PushbackReader in = new PushbackReader(reader, 1);
        int ch;
        while ((ch = in.read()) != -1)
        {
            char c = (char) ch;
            if (inQuotes)
            {
                if (c == '"')
                {
                    int next = in.read();
                    if (next == '"')
                    {
                        field.append('"');
                    }
                    else
                    {
                        inQuotes = false;
                        if (next != -1)
                        {
                            in.unread(next);
                        }
                    }
                }
                else
                {
                    field.append(c);
                }
                continue;
            }
            if (c == '"')
            {
                inQuotes = true;
            }
            else if (c == ',')
            {
                row.add(field.toString());
                field.setLength(0);
            }
            else if (c == '\n')
            {
                row.add(field.toString());
                field.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
            }
            else if (c != '\r')
            {
                field.append(c);
            }
        }
        if (field.length() > 0 || !row.isEmpty())
        {
            row.add(field.toString());
            rows.add(row);
        }
        return rows;
    }
}
