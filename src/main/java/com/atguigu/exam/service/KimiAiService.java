package com.atguigu.exam.service;


import com.atguigu.exam.vo.AiGenerateRequestVo;
import com.atguigu.exam.vo.QuestionImportVo;

import java.util.List;

/**
 * Kimi AI服务接口
 * 用于调用Kimi API生成题目
 */
public interface KimiAiService {

    String buildPrompt(AiGenerateRequestVo request);

    List<QuestionImportVo> aigenerateQuestions(AiGenerateRequestVo request) throws InterruptedException;

    String callKimiAi(String prompt) throws InterruptedException;
}