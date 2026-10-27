package com.ruoyi.project.langchain;

import java.util.Collections;
import java.util.List;

/**
 * 大模型调用抽象，便于替换为 LangChain4j、测试桩或其它实现。
 *
 * @author ruoyi
 */
public interface ChatModelClient
{
    /**
     * 使用默认模型执行一次带 system prompt 的对话。
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt 用户输入
     * @return 模型回答
     */
    String chat(String systemPrompt, String userPrompt);

    /**
     * 使用指定模型执行一次带 system prompt 的对话。
     *
     * <p>默认实现忽略模型标识，回退到 {@link #chat(String, String)}，
     * 便于测试桩或单模型实现保持兼容。</p>
     *
     * @param modelId 模型标识，为空时使用默认模型
     * @param systemPrompt 系统提示词
     * @param userPrompt 用户输入
     * @return 模型回答
     */
    default String chat(String modelId, String systemPrompt, String userPrompt)
    {
        return chat(systemPrompt, userPrompt);
    }

    /**
     * 返回可选的模型列表，单模型实现返回空列表。
     */
    default List<ChatModelOption> listModels()
    {
        return Collections.emptyList();
    }
}
