package com.pangruixin.llm.mentor.agentx.service;

/**
 * 文件不存在、归属未知或当前用户无权访问时抛出的统一异常。
 *
 * <p>对外不区分“文件不存在”和“文件属于别人”，避免泄露资源是否存在。
 */
public class FileAccessDeniedException extends IllegalArgumentException {

    public FileAccessDeniedException() {
        super("文件不存在或无权访问");
    }

    public FileAccessDeniedException(String message) {
        super(message);
    }
}
