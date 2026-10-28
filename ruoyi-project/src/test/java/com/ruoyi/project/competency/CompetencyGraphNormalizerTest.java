package com.ruoyi.project.competency;

import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 用真实大模型输出（8 能力项 / 102 节点）验证 Normalizer 的契约稳定性。
 *
 * @author ruoyi
 */
public class CompetencyGraphNormalizerTest
{
    private static final String FIXTURE = "competency/llm_graph_v2.json";

    private static final Set<String> CATEGORIES = new HashSet<>();

    static
    {
        String[] values = {"INDUSTRY_CHAIN", "INDUSTRY_SEGMENT", "JOB_CLUSTER", "JOB",
                "CLASS_MAJOR", "CLASS_MIDDLE", "CLASS_MINOR", "CLASS_OCCUPATION",
                "WORK_TASK", "DOMAIN", "COMPETENCY", "COMPETENCY_UNIT",
                "SKILL_POINT", "KNOWLEDGE_POINT"};
        for (String value : values)
        {
            CATEGORIES.add(value);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> normalize() throws Exception
    {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> raw;
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(FIXTURE))
        {
            assertNotNull(inputStream, "fixture 缺失: " + FIXTURE);
            raw = mapper.readValue(inputStream, Map.class);
        }
        return new CompetencyGraphNormalizer().normalize(raw, "信息通信网络运行管理员");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testRealLlmOutputBecomesV3() throws Exception
    {
        Map<String, Object> out = normalize();
        assertEquals("3.0.0", out.get("schema_version"));

        List<Map<String, Object>> competencies = (List<Map<String, Object>>) out.get("competencies");
        assertEquals(8, competencies.size());
        for (Map<String, Object> competency : competencies)
        {
            List<Map<String, Object>> units = (List<Map<String, Object>>) competency.get("units");
            assertFalse(units.isEmpty(), "能力项缺少能力单元: " + competency.get("name"));
            for (Map<String, Object> unit : units)
            {
                assertFalse(((List<?>) unit.get("skills")).isEmpty(),
                        "能力单元缺少技能: " + unit.get("id"));
                assertFalse(((List<?>) unit.get("knowledge")).isEmpty(),
                        "能力单元缺少知识: " + unit.get("id"));
            }
        }

        Map<String, Object> review = (Map<String, Object>) out.get("review");
        assertEquals("pending", review.get("status"));

        Map<String, Object> views = (Map<String, Object>) out.get("views");
        Map<String, Object> radar = (Map<String, Object>) views.get("radar");
        List<Map<String, Object>> indicators = (List<Map<String, Object>>) radar.get("indicators");
        List<Map<String, Object>> series = (List<Map<String, Object>>) radar.get("series");
        assertFalse(indicators.isEmpty());
        assertFalse(series.isEmpty());
        assertEquals(indicators.size(), ((List<?>) series.get(0).get("values")).size());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testGraphNodesAndEdgesAreConsistent() throws Exception
    {
        Map<String, Object> views = (Map<String, Object>) normalize().get("views");
        Map<String, Object> graph = (Map<String, Object>) views.get("graph");
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) graph.get("nodes");
        List<Map<String, Object>> edges = (List<Map<String, Object>>) graph.get("edges");

        assertEquals(102, nodes.size());
        Set<String> nodeIds = new HashSet<>();
        for (Map<String, Object> node : nodes)
        {
            nodeIds.add(String.valueOf(node.get("id")));
            assertTrue(CATEGORIES.contains(String.valueOf(node.get("category"))),
                    "非法节点类别: " + node.get("category"));
        }
        for (Map<String, Object> edge : edges)
        {
            assertTrue(nodeIds.contains(String.valueOf(edge.get("source"))),
                    "边 source 不存在: " + edge.get("source"));
            assertTrue(nodeIds.contains(String.valueOf(edge.get("target"))),
                    "边 target 不存在: " + edge.get("target"));
        }
    }
}
