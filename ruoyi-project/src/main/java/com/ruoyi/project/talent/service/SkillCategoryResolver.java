package com.ruoyi.project.talent.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ruoyi.project.talent.mapper.TalentMapper;

/**
 * 技能 → 类别解析器（路径二：按技能逐个分类）。
 *
 * <p>顺序：字典表 {@code talent_skill_category} → 关键词规则 → 岗位方向默认类别。
 * 字典表可在库里维护，新增技能无需改代码；未登记的技能由关键词规则兜底。</p>
 */
@Component
public class SkillCategoryResolver
{
    private static final Logger log = LoggerFactory.getLogger(SkillCategoryResolver.class);

    /** 字典不可用时的岗位方向兜底 */
    private static final Map<String, String> FAMILY_FALLBACK = new LinkedHashMap<>();

    /** 新技能的关键词兜底规则（按顺序匹配，先命中先返回） */
    private static final List<Object[]> RULES = new ArrayList<>();

    static
    {
        FAMILY_FALLBACK.put("后端开发", "后端");
        FAMILY_FALLBACK.put("前端开发", "前端");
        FAMILY_FALLBACK.put("全栈/AI应用开发", "后端");
        FAMILY_FALLBACK.put("算法工程师", "AI大模型");
        FAMILY_FALLBACK.put("数据开发", "后端");
        FAMILY_FALLBACK.put("软件测试", "工程化运维");
        FAMILY_FALLBACK.put("运维/DevOps", "工程化运维");
        FAMILY_FALLBACK.put("C++/嵌入式开发", "后端");
        FAMILY_FALLBACK.put("产品/运营", "业务技能");
        FAMILY_FALLBACK.put("UI/视觉设计", "前端");

        rule("后端", "Java|Spring|MyBatis|MySQL|Redis|PostgreSQL|Oracle|SQL|Golang|Go语言|Node|PHP|C\\+\\+|C#|微服务|后端|接口|数据库|消息队列|Kafka|Flink|Hadoop|Hive|HBase");
        rule("前端", "Vue|React|Angular|TypeScript|JavaScript|HTML|CSS|Webpack|Vite|Next\\.js|Nuxt|小程序|前端|Three\\.js");
        rule("AI大模型", "大模型|LLM|RAG|Agent|智能体|PyTorch|TensorFlow|Transformer|Prompt|LangChain|Cursor|Claude|GPT|机器学习|深度学习|算法|NLP|计算机视觉");
        rule("工程化运维", "Docker|K8s|Kubernetes|Linux|Nginx|Git|CI/CD|Jenkins|运维|监控|部署|容器");
        rule("质量管控", "质量|检验|检测|品控");
        rule("设备维护", "设备|维修|维护|调试|电路|硬件|机电");
        rule("安全规范", "安全|消防|防护");
        rule("生产管理", "产线|装配|焊接|车间|生产|工艺");
        rule("临床护理", "护理|临床|康复|医学|药学|口腔|验光");
        rule("医患沟通", "医患|医患沟通");
        rule("人力资源", "招聘|人事|薪酬|绩效");
        rule("财务核算", "财务|会计|税务|审计|核算");
        rule("行政办公", "行政|办公|公文");
        rule("销售管理", "销售|渠道|谈判|招投标|大客户");
        rule("客户服务", "客户|客服|接待|沟通|服务");
        rule("运营策划", "运营|策划|推广|活动|新媒体|内容");
        rule("电商运营", "电商|店铺|跨境|直播带货");
        rule("仓储管理", "仓储|库存|物流|配送|快递|分拣|装卸|打包");
        rule("餐饮服务", "餐饮|后厨|点餐|收银|客房|食品安全");
        rule("结构设计", "设计|制图|建模|BIM|平面|交互|动画");
        rule("美容美发", "美容|美发|美甲|皮肤|化妆|造型");
        rule("健身指导", "健身|私教|理疗|健康咨询");
        rule("家政服务", "家政|保洁|月嫂|育儿");
        rule("旅游策划", "旅游|行程|会奖|婚庆|婚礼|摄影");
        rule("语言沟通", "英语|日语|韩语|外语|翻译");
        rule("金融知识", "金融|证券|基金|保险|理财");
        rule("农业技术", "农业|养殖|种植");
    }

    private static void rule(String category, String regex)
    {
        RULES.add(new Object[] { Pattern.compile(regex), category });
    }

    @Autowired
    private TalentMapper talentMapper;

    private volatile Map<String, String> dict;

    /**
     * 解析技能类别。
     *
     * @param skill     技能名
     * @param jobFamily 岗位方向（兜底用）
     */
    public String resolve(String skill, String jobFamily)
    {
        String name = skill == null ? "" : skill.trim();
        if (!name.isEmpty())
        {
            String fromDict = dictionary().get(name);
            if (fromDict != null && !fromDict.isEmpty())
            {
                return fromDict;
            }
            for (Object[] item : RULES)
            {
                if (((Pattern) item[0]).matcher(name).find())
                {
                    return (String) item[1];
                }
            }
        }
        String fallback = FAMILY_FALLBACK.get(jobFamily);
        return fallback == null ? "其他" : fallback;
    }

    /** 字典更新后调用，下次解析重新加载 */
    public void refresh()
    {
        dict = null;
    }

    private Map<String, String> dictionary()
    {
        Map<String, String> map = dict;
        if (map == null)
        {
            synchronized (this)
            {
                map = dict;
                if (map == null)
                {
                    map = loadDictionary();
                    dict = map;
                }
            }
        }
        return map;
    }

    private Map<String, String> loadDictionary()
    {
        Map<String, String> map = new LinkedHashMap<>();
        try
        {
            List<Map<String, Object>> rows = talentMapper.selectSkillCategoryDict();
            if (rows != null)
            {
                for (Map<String, Object> row : rows)
                {
                    Object name = row.get("skillName");
                    Object category = row.get("category");
                    if (name != null && category != null)
                    {
                        map.put(String.valueOf(name).trim(), String.valueOf(category).trim());
                    }
                }
            }
            log.info("技能类别字典加载完成：{} 条", map.size());
        }
        catch (Exception e)
        {
            log.warn("技能类别字典加载失败，改用关键词规则兜底", e);
        }
        return Collections.unmodifiableMap(map);
    }
}
