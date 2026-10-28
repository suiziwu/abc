package com.ruoyi.project.langchain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * LangGraph StateGraph 语义执行器。
 *
 * <p>按清单中的节点类型依次执行：start → code → router → llm → end，
 * 路由使用条件分支，LLM 通过 {@link ChatModelClient} 调用（默认 LangChain4j/DeepSeek）。</p>
 *
 * @author ruoyi
 */
@Component
public class LangChainWorkflowExecutor
{
    private static final Logger log = LoggerFactory.getLogger(LangChainWorkflowExecutor.class);

    @Autowired
    private ObjectMapper objectMapper;
    public LangChainWorkflowResult execute(LangChainWorkflow workflow, ChatModelClient chatModelClient,
            String input)
    {
        return execute(workflow, chatModelClient, input, null);
    }

    public LangChainWorkflowResult execute(LangChainWorkflow workflow, ChatModelClient chatModelClient,
            String input, Map<String, Object> extraState)
    {
        long started = System.nanoTime();
        Map<String, LangChainWorkflow.Node> nodeById = new LinkedHashMap<>();
        for (LangChainWorkflow.Node node : workflow.getGraph().getNodes())
        {
            nodeById.put(node.getId(), node);
        }

        Map<String, Object> state = new LinkedHashMap<>();
        if (workflow.getState().getDefaults() != null)
        {
            state.putAll(workflow.getState().getDefaults());
        }
        List<String> inputKeys = workflow.getExecution().getInputKeys();
        if (inputKeys == null || inputKeys.isEmpty())
        {
            throw new LangChainWorkflowException("execution.input_keys 未配置");
        }
        state.put(inputKeys.get(0), input);
        if (extraState != null && !extraState.isEmpty())
        {
            for (Map.Entry<String, Object> entry : extraState.entrySet())
            {
                if (entry.getKey() != null && !inputKeys.contains(entry.getKey()))
                {
                    state.put(entry.getKey(), entry.getValue());
                }
            }
        }

        List<String> path = new ArrayList<>();
        Map<String, String> modelOutputs = new LinkedHashMap<>();
        int limit = workflow.getExecution().getRecursionLimit() == null
                ? 25 : workflow.getExecution().getRecursionLimit();
        String currentId = workflow.getGraph().getEntrypoint();
        int steps = 0;
        while (currentId != null)
        {
            if (++steps > limit)
            {
                throw new LangChainWorkflowException("工作流执行超过 recursion_limit: " + limit);
            }
            LangChainWorkflow.Node node = nodeById.get(currentId);
            if (node == null)
            {
                throw new LangChainWorkflowException("节点不存在: " + currentId);
            }
            path.add(node.getRuntimeName());

            String type = node.getType();
            if ("start".equals(type))
            {
                currentId = nextUnconditionalTarget(workflow, currentId);
            }
            else if ("code".equals(type))
            {
                executeCode(node, state);
                currentId = nextUnconditionalTarget(workflow, currentId);
            }
            else if ("router".equals(type))
            {
                currentId = evaluateRouter(workflow, node, state);
            }
            else if ("llm".equals(type))
            {
                executeLlm(node, state, chatModelClient, modelOutputs);
                currentId = nextUnconditionalTarget(workflow, currentId);
            }
            else if ("end".equals(type))
            {
                executeEnd(workflow, node, state);
                currentId = null;
            }
            else
            {
                throw new LangChainWorkflowException("不支持的节点类型: " + type);
            }
        }

        LangChainWorkflowResult result = new LangChainWorkflowResult();
        result.setInput(input);
        result.setOutput(stringValue(state.get(workflow.getExecution().getOutputKey())));
        result.setPath(path);
        result.setState(new LinkedHashMap<>(state));
        result.setModelOutputs(modelOutputs);
        result.setSteps(steps);
        result.setDurationMs((System.nanoTime() - started) / 1000000L);
        log.info("工作流 {} 执行完成，节点 {} 个，耗时 {} ms",
                workflow.getFlow() == null ? "" : workflow.getFlow().getName(),
                steps, result.getDurationMs());
        return result;
    }

    private void executeCode(LangChainWorkflow.Node node, Map<String, Object> state)
    {
        LangChainWorkflow.CodeOperation operation = node.getCode().getOperation();
        String input = stringValue(state.get(operation.getInputStateKey()));
        int splitIndex = operation.getSplitIndex();
        String head;
        String tail;
        if (input.length() >= splitIndex)
        {
            head = input.substring(0, splitIndex);
            tail = input.substring(splitIndex);
        }
        else
        {
            // 与原始 Python 代码一致：长度不足时 code=input, item=input
            head = input;
            tail = input;
        }
        state.put(operation.getOutputs().get("head"), head);
        state.put(operation.getOutputs().get("tail"), tail);
    }

    private String evaluateRouter(LangChainWorkflow workflow, LangChainWorkflow.Node node,
            Map<String, Object> state)
    {
        LangChainWorkflow.ConditionalEdge conditionalEdge = null;
        for (LangChainWorkflow.ConditionalEdge item : workflow.getGraph().getConditionalEdges())
        {
            if (node.getId().equals(item.getSource()))
            {
                conditionalEdge = item;
                break;
            }
        }
        if (conditionalEdge == null)
        {
            throw new LangChainWorkflowException("router 缺少 conditional_edges: " + node.getId());
        }
        for (LangChainWorkflow.Branch branch : conditionalEdge.getBranches())
        {
            List<LangChainWorkflow.Condition> conditions = branch.getConditions();
            if (conditions == null || conditions.isEmpty())
            {
                if (Boolean.TRUE.equals(branch.getIsDefault()))
                {
                    return branch.getTarget();
                }
                continue;
            }
            boolean matched = "or".equalsIgnoreCase(branch.getLogicalOperator()) ? false : true;
            for (LangChainWorkflow.Condition condition : conditions)
            {
                boolean conditionValue = evaluateCondition(condition, state);
                if ("or".equalsIgnoreCase(branch.getLogicalOperator()))
                {
                    matched = matched || conditionValue;
                }
                else
                {
                    matched = matched && conditionValue;
                }
            }
            if (matched)
            {
                return branch.getTarget();
            }
        }
        return conditionalEdge.getDefaultTarget();
    }

    private boolean evaluateCondition(LangChainWorkflow.Condition condition, Map<String, Object> state)
    {
        String left = resolveOperand(condition.getLeft(), state);
        String right = resolveOperand(condition.getRight(), state);
        String operator = condition.getOperator() == null ? "eq" : condition.getOperator();
        if ("eq".equals(operator))
        {
            return left.equals(right);
        }
        if ("ne".equals(operator))
        {
            return !left.equals(right);
        }
        if ("contains".equals(operator))
        {
            return left.contains(right);
        }
        int compare = left.compareTo(right);
        try
        {
            compare = Double.compare(Double.parseDouble(left.trim()), Double.parseDouble(right.trim()));
        }
        catch (NumberFormatException ignored)
        {
            // 非数字按字符串比较
        }
        if ("gt".equals(operator))
        {
            return compare > 0;
        }
        if ("gte".equals(operator))
        {
            return compare >= 0;
        }
        if ("lt".equals(operator))
        {
            return compare < 0;
        }
        if ("lte".equals(operator))
        {
            return compare <= 0;
        }
        throw new LangChainWorkflowException("不支持的条件操作符: " + operator);
    }

    private String resolveOperand(LangChainWorkflow.Operand operand, Map<String, Object> state)
    {
        if (operand == null)
        {
            return "";
        }
        if ("ref".equals(operand.getKind()))
        {
            return stringValue(state.get(operand.getStateKey()));
        }
        if ("literal".equals(operand.getKind()))
        {
            return stringValue(operand.getValue());
        }
        return "";
    }

    private void executeLlm(LangChainWorkflow.Node node, Map<String, Object> state,
            ChatModelClient chatModelClient, Map<String, String> modelOutputs)
    {
        LangChainWorkflow.Llm llm = node.getLlm();
        if (llm == null || llm.getPrompt() == null)
        {
            throw new LangChainWorkflowException("LLM 节点配置缺失: " + node.getId());
        }
        if ("graph_assembler".equals(node.getRuntimeName()))
        {
            String assembled = assembleFromState(state);
            if (assembled != null)
            {
                log.info("装配节点由 Java 合并，跳过 LLM，输出 {} 字", assembled.length());
                state.put(llm.getOutput().getStateKey(), assembled);
                modelOutputs.put(llm.getOutput().getStateKey(), assembled);
                return;
            }
        }
        String userPrompt = renderTemplate(llm.getPrompt().getUserTemplate(),
                llm.getPrompt().getInputMapping(), state);
        String chainContext = stringValue(state.get("chain_context"));
        if (!chainContext.isEmpty() && "industry_chain_mapper".equals(node.getRuntimeName()))
        {
            userPrompt = userPrompt + "\n\n" + chainContext;
        }
        String officialContext = stringValue(state.get("official_context"));
        if (!officialContext.isEmpty() && isEvidenceNode(node))
        {
            userPrompt = userPrompt + "\n\n【官方参考数据（优先使用，不得编造；缺失时才由模型补充）】\n"
                    + officialContext;
        }
        long started = System.nanoTime();
        String modelId = llm.getModel();
        String answer;
        if (modelId == null || modelId.trim().isEmpty() || "default".equalsIgnoreCase(modelId))
        {
            answer = chatModelClient.chat(llm.getPrompt().getSystem(), userPrompt);
        }
        else
        {
            answer = chatModelClient.chat(modelId, llm.getPrompt().getSystem(), userPrompt);
        }
        if (answer == null)
        {
            answer = "";
        }
        long costMs = (System.nanoTime() - started) / 1000000L;
        log.info("LLM 节点 {} 完成，耗时 {} ms，输入 {} 字，输出 {} 字",
                node.getRuntimeName(), costMs, userPrompt.length(), answer.length());
        state.put(llm.getOutput().getStateKey(), answer);
        modelOutputs.put(llm.getOutput().getStateKey(), answer);
    }

    private boolean isEvidenceNode(LangChainWorkflow.Node node)
    {
        String runtimeName = node.getRuntimeName();
        return "competency_decomposer".equals(runtimeName)
                || "element_mapper".equals(runtimeName)
                || "task_analyzer".equals(runtimeName);
    }

    /**
     * 用前四步的模型输出在 Java 侧合并图谱，跳过第五次 LLM 装配调用。
     */
    private String assembleFromState(Map<String, Object> state)
    {
        Map<String, Object> chain = parseJson(state.get("industry_chain"));
        Map<String, Object> tasksRoot = parseJson(state.get("work_tasks"));
        Map<String, Object> competenciesRoot = parseJson(state.get("competencies"));
        Map<String, Object> elementsRoot = parseJson(state.get("elements"));
        if (chain == null || tasksRoot == null || competenciesRoot == null || elementsRoot == null)
        {
            return null;
        }
        List<Map<String, Object>> tasks = asList(tasksRoot.get("tasks"));
        List<Map<String, Object>> competencies = asList(competenciesRoot.get("competencies"));
        if (competencies.isEmpty())
        {
            return null;
        }

        Map<String, Map<String, Object>> taskById = new LinkedHashMap<String, Map<String, Object>>();
        for (Map<String, Object> task : tasks)
        {
            taskById.put(text(task.get("id")), task);
        }

        double maxScore = 0D;
        for (Map<String, Object> competency : competencies)
        {
            double score = 0D;
            int count = 0;
            for (String taskId : toStringList(competency.get("task_ids")))
            {
                Map<String, Object> task = taskById.get(taskId);
                if (task == null)
                {
                    continue;
                }
                score += (number(task.get("importance")) + number(task.get("frequency"))
                        + number(task.get("difficulty"))) / 3D;
                count++;
            }
            double average = count == 0 ? 0D : score / count;
            competency.put("weight", average);
            if (average > maxScore)
            {
                maxScore = average;
            }
        }
        for (Map<String, Object> competency : competencies)
        {
            double average = number(competency.get("weight"));
            int weight = maxScore > 0D ? (int) Math.round(average / maxScore * 100D) : 0;
            competency.put("weight", weight);
        }

        List<Map<String, Object>> skills = new ArrayList<Map<String, Object>>();
        List<Map<String, Object>> knowledge = new ArrayList<Map<String, Object>>();
        Map<String, Integer> skillCounts = new LinkedHashMap<String, Integer>();
        Map<String, Integer> knowledgeCounts = new LinkedHashMap<String, Integer>();
        Map<String, Object> elements = asMap(elementsRoot.get("elements"));
        for (Map<String, Object> item : asList(elements.get("competencies")))
        {
            String competencyId = text(item.get("id"));
            for (Map<String, Object> skill : asList(item.get("skills")))
            {
                Map<String, Object> copy = new LinkedHashMap<String, Object>(skill);
                copy.put("competency_ids", singletonList(competencyId));
                skills.add(copy);
                skillCounts.put(competencyId, skillCounts.getOrDefault(competencyId, 0) + 1);
            }
            for (Map<String, Object> point : asList(item.get("knowledge")))
            {
                Map<String, Object> copy = new LinkedHashMap<String, Object>(point);
                copy.put("competency_ids", singletonList(competencyId));
                knowledge.add(copy);
                knowledgeCounts.put(competencyId, knowledgeCounts.getOrDefault(competencyId, 0) + 1);
            }
        }
        if (skills.isEmpty() || knowledge.isEmpty())
        {
            return null;
        }
        for (Map<String, Object> competency : competencies)
        {
            String competencyId = text(competency.get("id"));
            if (skillCounts.getOrDefault(competencyId, 0) == 0
                    || knowledgeCounts.getOrDefault(competencyId, 0) == 0)
            {
                return null;
            }
        }

        Map<String, Object> flatElements = new LinkedHashMap<String, Object>();
        flatElements.put("skills", skills);
        flatElements.put("knowledge", knowledge);

        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("job", chain.get("job"));
        out.put("industry_chain", chain.get("industry_chain"));
        out.put("logic_chain", chain.get("logic_chain"));
        out.put("tasks", tasks);
        out.put("competencies", competencies);
        out.put("elements", flatElements);
        out.put("confidence", chain.get("confidence"));
        out.put("sources", chain.get("sources"));
        try
        {
            return objectMapper.writeValueAsString(out);
        }
        catch (Exception e)
        {
            log.warn("Java 合并图谱失败，回退到 LLM 装配: {}", e.getMessage());
            return null;
        }
    }

    private List<String> singletonList(String value)
    {
        List<String> result = new ArrayList<String>();
        if (value != null && !value.isEmpty())
        {
            result.add(value);
        }
        return result;
    }

    private Map<String, Object> parseJson(Object value)
    {
        String text = stringValue(value);
        if (text.isEmpty())
        {
            return null;
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start)
        {
            return null;
        }
        try
        {
            return objectMapper.readValue(text.substring(start, end + 1),
                    new TypeReference<Map<String, Object>>() {});
        }
        catch (Exception e)
        {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value)
    {
        return value instanceof Map ? (Map<String, Object>) value
                : new LinkedHashMap<String, Object>();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asList(Object value)
    {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        if (value instanceof List)
        {
            for (Object item : (List<Object>) value)
            {
                if (item instanceof Map)
                {
                    result.add((Map<String, Object>) item);
                }
            }
        }
        return result;
    }

    private List<String> toStringList(Object value)
    {
        List<String> result = new ArrayList<String>();
        if (value instanceof List)
        {
            for (Object item : (List<?>) value)
            {
                if (item != null)
                {
                    result.add(String.valueOf(item));
                }
            }
        }
        else if (value != null)
        {
            result.add(String.valueOf(value));
        }
        return result;
    }

    private String text(Object value)
    {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private double number(Object value)
    {
        if (value instanceof Number)
        {
            return ((Number) value).doubleValue();
        }
        try
        {
            return Double.parseDouble(stringValue(value));
        }
        catch (Exception e)
        {
            return 0D;
        }
    }

    private void executeEnd(LangChainWorkflow workflow, LangChainWorkflow.Node node,
            Map<String, Object> state)
    {
        LangChainWorkflow.End end = node.getEnd();
        if (end == null)
        {
            throw new LangChainWorkflowException("end 节点配置缺失: " + node.getId());
        }
        String output = renderTemplate(end.getTemplate(), null, state);
        state.put(workflow.getExecution().getOutputKey(), output);
    }

    /**
     * 渲染 {{var}} 模板。传入 mapping 时只替换映射变量；否则按状态键全量替换。
     */
    private String renderTemplate(String template, Map<String, String> mapping,
            Map<String, Object> state)
    {
        if (template == null)
        {
            return "";
        }
        String result = template;
        if (mapping != null && !mapping.isEmpty())
        {
            for (Map.Entry<String, String> entry : mapping.entrySet())
            {
                result = result.replace("{{" + entry.getKey() + "}}",
                        stringValue(state.get(entry.getValue())));
            }
            return result;
        }
        for (Map.Entry<String, Object> entry : state.entrySet())
        {
            result = result.replace("{{" + entry.getKey() + "}}", stringValue(entry.getValue()));
        }
        return result;
    }

    private String nextUnconditionalTarget(LangChainWorkflow workflow, String currentId)
    {
        for (LangChainWorkflow.Edge edge : workflow.getGraph().getEdges())
        {
            if (!currentId.equals(edge.getSource()))
            {
                continue;
            }
            if (edge.getSourceHandle() == null || edge.getSourceHandle().isEmpty())
            {
                return edge.getTarget();
            }
        }
        return null;
    }

    private static String stringValue(Object value)
    {
        return value == null ? "" : String.valueOf(value);
    }
}
