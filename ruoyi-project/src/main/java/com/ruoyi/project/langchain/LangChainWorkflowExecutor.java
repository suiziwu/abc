package com.ruoyi.project.langchain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

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
    public LangChainWorkflowResult execute(LangChainWorkflow workflow, ChatModelClient chatModelClient,
            String input)
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
        String userPrompt = renderTemplate(llm.getPrompt().getUserTemplate(),
                llm.getPrompt().getInputMapping(), state);
        String answer = chatModelClient.chat(llm.getPrompt().getSystem(), userPrompt);
        if (answer == null)
        {
            answer = "";
        }
        state.put(llm.getOutput().getStateKey(), answer);
        modelOutputs.put(llm.getOutput().getStateKey(), answer);
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
