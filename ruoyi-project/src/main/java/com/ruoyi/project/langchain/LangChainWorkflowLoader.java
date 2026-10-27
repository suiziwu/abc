package com.ruoyi.project.langchain;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.springframework.stereotype.Component;

/**
 * LangGraph StateGraph 清单加载与校验器。
 *
 * <p>校验项：JSON 语法、必填字段、节点/边引用、分支目标、可达性、Prompt 与状态键、
 * 原始讯飞 DSL 条目数和 ID 集合一致性。</p>
 *
 * @author ruoyi
 */
@Component
public class LangChainWorkflowLoader
{
    private final ObjectMapper objectMapper;

    public LangChainWorkflowLoader()
    {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public ObjectMapper getObjectMapper()
    {
        return objectMapper;
    }

    public LangChainWorkflow load(InputStream inputStream) throws IOException
    {
        LangChainWorkflow workflow = objectMapper.readValue(inputStream, LangChainWorkflow.class);
        validate(workflow);
        return workflow;
    }

    public void validate(LangChainWorkflow workflow)
    {
        require(workflow != null, "工作流清单为空");
        require(workflow.getSchemaVersion() != null, "缺少 schema_version");
        require("langchain/langgraph".equals(workflow.getFramework()), "framework 必须为 langchain/langgraph");
        require("langgraph-state-graph".equals(workflow.getFormat()), "format 必须为 langgraph-state-graph");
        require(workflow.getFlow() != null, "缺少 flow");
        require(workflow.getFlow().getName() != null, "缺少 flow.name");
        require(workflow.getFlow().getAdvancedConfigRaw() != null, "缺少 flow.advanced_config_raw");
        require(workflow.getState() != null, "缺少 state");
        require(workflow.getState().getSchema() != null && !workflow.getState().getSchema().isEmpty(),
                "state.schema 不能为空");
        require(workflow.getModels() != null && !workflow.getModels().isEmpty(), "models 不能为空");
        require(workflow.getGraph() != null, "缺少 graph");
        require(workflow.getExecution() != null, "缺少 execution");

        LangChainWorkflow.Graph graph = workflow.getGraph();
        List<LangChainWorkflow.Node> nodes = graph.getNodes();
        List<LangChainWorkflow.Edge> edges = graph.getEdges();
        require(nodes != null && !nodes.isEmpty(), "graph.nodes 不能为空");
        require(edges != null, "graph.edges 不能为空");

        Map<String, LangChainWorkflow.Node> nodeById = new LinkedHashMap<>();
        Set<String> runtimeNames = new HashSet<>();
        for (LangChainWorkflow.Node node : nodes)
        {
            require(node != null && node.getId() != null, "存在缺少 id 的节点");
            require(!nodeById.containsKey(node.getId()), "重复节点 ID: " + node.getId());
            require(node.getRuntimeName() != null, "节点缺少 runtime_name: " + node.getId());
            require(!runtimeNames.contains(node.getRuntimeName()),
                    "重复 runtime_name: " + node.getRuntimeName());
            require(node.getType() != null, "节点缺少 type: " + node.getId());
            nodeById.put(node.getId(), node);
            runtimeNames.add(node.getRuntimeName());
        }

        int startCount = 0;
        int endCount = 0;
        int routerCount = 0;
        for (LangChainWorkflow.Node node : nodes)
        {
            if ("start".equals(node.getType()))
            {
                startCount++;
            }
            else if ("end".equals(node.getType()))
            {
                endCount++;
            }
            else if ("router".equals(node.getType()))
            {
                routerCount++;
            }
        }
        require(startCount == 1, "start 节点数量必须为 1");
        require(endCount == 1, "end 节点数量必须为 1");

        for (LangChainWorkflow.Edge edge : edges)
        {
            require(edge.getId() != null, "存在缺少 id 的边");
            require(nodeById.containsKey(edge.getSource()), "边 source 不存在: " + edge.getSource());
            require(nodeById.containsKey(edge.getTarget()), "边 target 不存在: " + edge.getTarget());
        }

        List<LangChainWorkflow.ConditionalEdge> conditionalEdges = graph.getConditionalEdges();
        require(conditionalEdges != null, "graph.conditional_edges 不能为空");
        require(conditionalEdges.size() == routerCount, "conditional_edges 数量与 router 节点数量不一致");

        Map<String, LangChainWorkflow.ConditionalEdge> conditionalBySource = new HashMap<>();
        for (LangChainWorkflow.ConditionalEdge conditionalEdge : conditionalEdges)
        {
            LangChainWorkflow.Node router = nodeById.get(conditionalEdge.getSource());
            require(router != null && "router".equals(router.getType()),
                    "conditional_edge source 不是 router: " + conditionalEdge.getSource());
            require(router.getRuntimeName().equals(conditionalEdge.getSourceRuntime()),
                    "conditional_edge source_runtime 不匹配");
            require(nodeById.containsKey(conditionalEdge.getDefaultTarget()),
                    "router 默认目标不存在: " + conditionalEdge.getDefaultTarget());
            require(conditionalEdge.getBranches() != null && !conditionalEdge.getBranches().isEmpty(),
                    "router 分支不能为空");
            for (LangChainWorkflow.Branch branch : conditionalEdge.getBranches())
            {
                require(branch.getBranchId() != null, "分支缺少 branch_id");
                require(nodeById.containsKey(branch.getTarget()),
                        "分支目标不存在: " + branch.getTarget());
                LangChainWorkflow.Node target = nodeById.get(branch.getTarget());
                require(target.getRuntimeName().equals(branch.getTargetRuntime()),
                        "分支 target_runtime 不匹配: " + branch.getBranchId());
                if (branch.getConditions() != null)
                {
                    for (LangChainWorkflow.Condition condition : branch.getConditions())
                    {
                        validateOperand(condition.getLeft(), workflow);
                        validateOperand(condition.getRight(), workflow);
                    }
                }
            }
            conditionalBySource.put(conditionalEdge.getSource(), conditionalEdge);
        }

        // 可达性
        Map<String, Set<String>> adjacency = new HashMap<>();
        for (LangChainWorkflow.Edge edge : edges)
        {
            if (edge.getSourceHandle() != null && !edge.getSourceHandle().isEmpty())
            {
                continue;
            }
            adjacency.computeIfAbsent(edge.getSource(), key -> new HashSet<>()).add(edge.getTarget());
        }
        for (LangChainWorkflow.ConditionalEdge conditionalEdge : conditionalEdges)
        {
            Set<String> targets = adjacency.computeIfAbsent(conditionalEdge.getSource(),
                    key -> new HashSet<>());
            for (LangChainWorkflow.Branch branch : conditionalEdge.getBranches())
            {
                targets.add(branch.getTarget());
            }
        }
        Set<String> visited = new HashSet<>();
        Deque<String> stack = new ArrayDeque<>();
        stack.push(graph.getEntrypoint());
        while (!stack.isEmpty())
        {
            String current = stack.pop();
            if (!visited.add(current))
            {
                continue;
            }
            Set<String> targets = adjacency.get(current);
            if (targets != null)
            {
                for (String target : targets)
                {
                    stack.push(target);
                }
            }
        }
        Set<String> unreachable = new HashSet<>(nodeById.keySet());
        unreachable.removeAll(visited);
        require(unreachable.isEmpty(), "存在不可达节点: " + unreachable);

        // 节点语义
        for (LangChainWorkflow.Node node : nodes)
        {
            if ("llm".equals(node.getType()))
            {
                validateLlm(node, workflow);
            }
            else if ("code".equals(node.getType()))
            {
                validateCode(node, workflow);
            }
            else if ("end".equals(node.getType()))
            {
                validateEnd(node, workflow);
            }
        }

        LangChainWorkflow.Execution execution = workflow.getExecution();
        LangChainWorkflow.Node start = findSingle(nodes, "start");
        LangChainWorkflow.Node end = findSingle(nodes, "end");
        require(execution.getEntrypoint() != null && execution.getEntrypoint().equals(start.getRuntimeName()),
                "execution.entrypoint 与 start 节点不一致");
        require(execution.getFinishPoint() != null && execution.getFinishPoint().equals(end.getRuntimeName()),
                "execution.finish_point 与 end 节点不一致");
        require(execution.getInputKeys() != null && !execution.getInputKeys().isEmpty(),
                "execution.input_keys 不能为空");
        require(execution.getOutputKey() != null
                        && workflow.getState().getSchema().containsKey(execution.getOutputKey()),
                "execution.output_key 未在状态中定义");
        require(execution.getOutputTemplate() != null
                        && execution.getOutputTemplate().equals(end.getEnd().getTemplate()),
                "execution.output_template 与结束节点模板不一致");
        require(execution.getRecursionLimit() != null && execution.getRecursionLimit() > 0,
                "execution.recursion_limit 必须大于 0");

        validateSourceConsistency(workflow, nodes, edges);
    }

    private void validateOperand(LangChainWorkflow.Operand operand, LangChainWorkflow workflow)
    {
        if (operand == null)
        {
            return;
        }
        if ("ref".equals(operand.getKind()))
        {
            require(operand.getStateKey() != null
                            && workflow.getState().getSchema().containsKey(operand.getStateKey()),
                    "条件引用了未定义状态键: " + operand.getStateKey());
        }
    }

    private void validateLlm(LangChainWorkflow.Node node, LangChainWorkflow workflow)
    {
        LangChainWorkflow.Llm llm = node.getLlm();
        require(llm != null, "LLM 节点缺少 llm 配置: " + node.getId());
        require(llm.getModel() != null && workflow.getModels().containsKey(llm.getModel()),
                "LLM 节点引用了未定义模型: " + node.getId());
        require(llm.getPrompt() != null, "LLM 节点缺少 prompt: " + node.getId());
        require(notBlank(llm.getPrompt().getSystem()), "LLM 节点缺少 system prompt: " + node.getId());
        require(notBlank(llm.getPrompt().getUserTemplate()),
                "LLM 节点缺少 user_template: " + node.getId());
        require(llm.getOutput() != null && llm.getOutput().getStateKey() != null,
                "LLM 节点缺少 output.state_key: " + node.getId());
        require(workflow.getState().getSchema().containsKey(llm.getOutput().getStateKey()),
                "LLM 输出状态键未定义: " + llm.getOutput().getStateKey());
        if (llm.getPrompt().getInputMapping() != null)
        {
            for (String stateKey : llm.getPrompt().getInputMapping().values())
            {
                require(workflow.getState().getSchema().containsKey(stateKey),
                        "LLM 输入映射状态键不存在: " + stateKey);
            }
        }
    }

    private void validateCode(LangChainWorkflow.Node node, LangChainWorkflow workflow)
    {
        LangChainWorkflow.Code code = node.getCode();
        require(code != null, "code 节点缺少 code 配置: " + node.getId());
        require(notBlank(code.getSource()), "code 节点缺少 python 源码: " + node.getId());
        require(code.getOperation() != null, "code 节点缺少 operation: " + node.getId());
        require("split_at".equals(code.getOperation().getType()),
                "code 节点 operation 类型不支持: " + code.getOperation().getType());
        require(code.getOperation().getInputStateKey() != null
                        && workflow.getState().getSchema().containsKey(code.getOperation().getInputStateKey()),
                "code 输入状态键不存在: " + code.getOperation().getInputStateKey());
        require(code.getOperation().getSplitIndex() != null && code.getOperation().getSplitIndex() >= 0,
                "code split_index 非法");
        require(code.getOperation().getOutputs() != null, "code operation 缺少 outputs");
        for (String stateKey : code.getOperation().getOutputs().values())
        {
            require(workflow.getState().getSchema().containsKey(stateKey),
                    "code 输出状态键不存在: " + stateKey);
        }
        if (code.getStateWrites() != null)
        {
            for (String stateKey : code.getStateWrites())
            {
                require(workflow.getState().getSchema().containsKey(stateKey),
                        "code state_writes 状态键未定义: " + stateKey);
            }
        }
    }

    private void validateEnd(LangChainWorkflow.Node node, LangChainWorkflow workflow)
    {
        LangChainWorkflow.End end = node.getEnd();
        require(end != null, "end 节点缺少 output 配置");
        require(notBlank(end.getTemplate()), "end 节点缺少输出模板");
        require(end.getStateKeys() != null && !end.getStateKeys().isEmpty(),
                "end 节点缺少 state_keys");
        for (String stateKey : end.getStateKeys())
        {
            require(workflow.getState().getSchema().containsKey(stateKey),
                    "end 节点引用未定义状态键: " + stateKey);
            require(end.getTemplate().contains("{{" + stateKey + "}}"),
                    "end 模板缺少占位符: {{" + stateKey + "}}");
        }
    }

    @SuppressWarnings("unchecked")
    private void validateSourceConsistency(LangChainWorkflow workflow,
            List<LangChainWorkflow.Node> nodes, List<LangChainWorkflow.Edge> edges)
    {
        Map<String, Object> extensions = workflow.getExtensions();
        require(extensions != null, "缺少 extensions");
        Object sourceObject = extensions.get("source");
        require(sourceObject instanceof Map, "extensions.source 缺失");
        Map<String, Object> source = (Map<String, Object>) sourceObject;
        Object flowDataObject = source.get("flowData");
        require(flowDataObject instanceof Map, "extensions.source.flowData 缺失");
        Map<String, Object> flowData = (Map<String, Object>) flowDataObject;
        List<Object> sourceNodes = asList(flowData.get("nodes"));
        List<Object> sourceEdges = asList(flowData.get("edges"));
        require(sourceNodes.size() == nodes.size(),
                "原始节点数与转换后节点数不一致: " + sourceNodes.size() + " != " + nodes.size());
        require(sourceEdges.size() == edges.size(),
                "原始边数与转换后边数不一致: " + sourceEdges.size() + " != " + edges.size());

        Set<String> sourceNodeIds = new HashSet<>();
        for (Object item : sourceNodes)
        {
            sourceNodeIds.add(String.valueOf(((Map<String, Object>) item).get("id")));
        }
        Set<String> sourceEdgeIds = new HashSet<>();
        for (Object item : sourceEdges)
        {
            sourceEdgeIds.add(String.valueOf(((Map<String, Object>) item).get("id")));
        }
        Set<String> nodeIds = new HashSet<>();
        for (LangChainWorkflow.Node node : nodes)
        {
            nodeIds.add(node.getId());
        }
        Set<String> edgeIds = new HashSet<>();
        for (LangChainWorkflow.Edge edge : edges)
        {
            edgeIds.add(edge.getId());
        }
        require(sourceNodeIds.equals(nodeIds), "原始节点 ID 集合与转换后不一致");
        require(sourceEdgeIds.equals(edgeIds), "原始边 ID 集合与转换后不一致");
    }

    @SuppressWarnings("unchecked")
    private List<Object> asList(Object value)
    {
        require(value instanceof List, "原始数据格式错误，期望数组");
        return (List<Object>) value;
    }

    private LangChainWorkflow.Node findSingle(List<LangChainWorkflow.Node> nodes, String type)
    {
        LangChainWorkflow.Node result = null;
        for (LangChainWorkflow.Node node : nodes)
        {
            if (type.equals(node.getType()))
            {
                result = node;
            }
        }
        require(result != null, "缺少 " + type + " 节点");
        return result;
    }

    private static boolean notBlank(String value)
    {
        return value != null && !value.trim().isEmpty();
    }

    private static void require(boolean condition, String message)
    {
        if (!condition)
        {
            throw new LangChainWorkflowException(message);
        }
    }
}
