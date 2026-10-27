package com.ruoyi.project.asr;

/**
 * 语音识别异常。
 *
 * @author ruoyi
 */
public class AsrException extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    public AsrException(String message)
    {
        super(message);
    }

    public AsrException(String message, Throwable cause)
    {
        super(message, cause);
    }
}
