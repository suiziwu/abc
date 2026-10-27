package com.ruoyi.project.langchain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * LangChain 工作流执行结果。
 *
 * @author ruoyi
 */
public class LangChainWorkflowResult
{
    private String input;

    private String output;

    private List<String> path;

    private Map<String, Object> state = new LinkedHashMap<>();

    private Map<String, String> modelOutputs = new LinkedHashMap<>();

    private int steps;

    private long durationMs;

    public String getInput()
    {
        return input;
    }

    public void setInput(String input)
    {
        this.input = input;
    }

    public String getOutput()
    {
        return output;
    }

    public void setOutput(String output)
    {
        this.output = output;
    }

    public List<String> getPath()
    {
        return path;
    }

    public void setPath(List<String> path)
    {
        this.path = path;
    }

    public Map<String, Object> getState()
    {
        return state;
    }

    public void setState(Map<String, Object> state)
    {
        this.state = state;
    }

    public Map<String, String> getModelOutputs()
    {
        return modelOutputs;
    }

    public void setModelOutputs(Map<String, String> modelOutputs)
    {
        this.modelOutputs = modelOutputs;
    }

    public int getSteps()
    {
        return steps;
    }

    public void setSteps(int steps)
    {
        this.steps = steps;
    }

    public long getDurationMs()
    {
        return durationMs;
    }

    public void setDurationMs(long durationMs)
    {
        this.durationMs = durationMs;
    }
}
