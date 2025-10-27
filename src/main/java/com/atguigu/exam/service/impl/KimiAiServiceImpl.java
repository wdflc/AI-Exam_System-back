package com.atguigu.exam.service.impl;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.atguigu.exam.config.properties.KimiProperties;
import com.atguigu.exam.service.KimiAiService;
import com.atguigu.exam.vo.AiGenerateRequestVo;
import com.atguigu.exam.vo.QuestionImportVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Kimi AI生成服务实现类
 */
@Slf4j
@Service
public class KimiAiServiceImpl implements KimiAiService {
    private final KimiProperties kimiProperties;
    private final WebClient webClient;

    public KimiAiServiceImpl(KimiProperties kimiProperties, WebClient webClient) {
        this.kimiProperties = kimiProperties;
        this.webClient = webClient;
    }

    /**
     * 构建发送给AI的提示词
     */
    public String buildPrompt(AiGenerateRequestVo request) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("请为我生成").append(request.getCount()).append("道关于【")
                .append(request.getTopic()).append("】的题目。\n\n");

        prompt.append("要求：\n");

        // 题目类型要求
        if (request.getTypes() != null && !request.getTypes().isEmpty()) {
            List<String> typeList = Arrays.asList(request.getTypes().split(","));
            prompt.append("- 题目类型：");
            for (String type : typeList) {
                switch (type.trim()) {
                    case "CHOICE":
                        prompt.append("选择题");
                        if (request.getIncludeMultiple() != null && request.getIncludeMultiple()) {
                            prompt.append("(包含单选和多选)");
                        }
                        prompt.append(" ");
                        break;
                    case "JUDGE":
                        prompt.append("判断题（**重要：确保正确答案和错误答案的数量大致平衡，不要全部都是正确或错误**） ");
                        break;
                    case "TEXT":
                        prompt.append("简答题 ");
                        break;
                }
            }
            prompt.append("\n");
        }

        // 难度要求
        if (request.getDifficulty() != null) {
            String difficultyText = switch (request.getDifficulty()) {
                case "EASY" -> "简单";
                case "MEDIUM" -> "中等";
                case "HARD" -> "困难";
                default -> "中等";
            };
            prompt.append("- 难度等级：").append(difficultyText).append("\n");
        }

        // 额外要求
        if (request.getRequirements() != null && !request.getRequirements().isEmpty()) {
            prompt.append("- 特殊要求：").append(request.getRequirements()).append("\n");
        }

        // 判断题特别要求
        if (request.getTypes() != null && request.getTypes().contains("JUDGE")) {
            prompt.append("- **判断题特别要求**：\n");
            prompt.append("  * 确保生成的判断题中，正确答案(TRUE)和错误答案(FALSE)的数量尽量平衡\n");
            prompt.append("  * 不要所有判断题都是正确的或都是错误的\n");
            prompt.append("  * 错误的陈述应该是常见的误解或容易混淆的概念\n");
            prompt.append("  * 正确的陈述应该是重要的基础知识点\n");
        }

        prompt.append("\n请严格按照以下JSON格式返回，不要包含任何其他文字：\n");
        prompt.append("```json\n");
        prompt.append("{\n");
        prompt.append("  \"questions\": [\n");
        prompt.append("    {\n");
        prompt.append("      \"title\": \"题目内容\",\n");
        prompt.append("      \"type\": \"CHOICE|JUDGE|TEXT\",\n");
        prompt.append("      \"multi\": true/false,\n");
        prompt.append("      \"difficulty\": \"EASY|MEDIUM|HARD\",\n");
        prompt.append("      \"score\": 5,\n");
        prompt.append("      \"choices\": [\n");
        prompt.append("        {\"content\": \"选项内容\", \"isCorrect\": true/false, \"sort\": 1}\n");
        prompt.append("      ],\n");
        prompt.append("      \"answer\": \"TRUE或FALSE(判断题专用)|文本答案(简答题专用)\",\n");
        prompt.append("      \"analysis\": \"题目解析\"\n");
        prompt.append("    }\n");
        prompt.append("  ]\n");
        prompt.append("}\n");
        prompt.append("```\n\n");

        prompt.append("注意：\n");
        prompt.append("1. 选择题必须有choices数组，判断题和简答题设置answer字段\n");
        prompt.append("2. 多选题的multi字段设为true，单选题设为false\n");
        prompt.append("3. **判断题的answer字段只能是\"TRUE\"或\"FALSE\"，请确保答案分布合理**\n");
        prompt.append("4. 每道题都要有详细的解析\n");
        prompt.append("5. 题目要有实际价值，贴近实际应用场景\n");
        prompt.append("6. 严格按照JSON格式返回，确保可以正确解析\n");

        // 如果只生成判断题，额外强调答案平衡
        if (request.getTypes() != null && request.getTypes().equals("JUDGE") && request.getCount() > 1) {
            prompt.append("7. **判断题答案分布要求**：在").append(request.getCount()).append("道判断题中，");
            int halfCount = request.getCount() / 2;
            if (request.getCount() % 2 == 0) {
                prompt.append("请生成").append(halfCount).append("道正确(TRUE)和").append(halfCount).append("道错误(FALSE)的题目");
            } else {
                prompt.append("请生成约").append(halfCount).append("-").append(halfCount + 1).append("道正确(TRUE)和约").append(halfCount).append("-").append(halfCount + 1).append("道错误(FALSE)的题目");
            }
        }

        return prompt.toString();
    }

    @Override
    public List<QuestionImportVo> aigenerateQuestions(AiGenerateRequestVo request) throws InterruptedException {
        //生成提示词
        String prompt = buildPrompt(request);
        // 调用kimi 调用方法 获取结果
        String content = callKimiAi(prompt);
        int startIndex = content.indexOf("```json");
        int endIndex = content.lastIndexOf("```");
        //保证有数据 而且 下标要正确！
        if(startIndex != -1 && endIndex != -1 && startIndex < endIndex){
            //获取真正的结果
            String realResult = content.substring(startIndex+7,endIndex);
            System.out.println("realResult = " + realResult);
            JSONObject jsonObject = JSONObject.parseObject(realResult);
            JSONArray questions = jsonObject.getJSONArray("questions");
            List<QuestionImportVo> questionImportVoList = new ArrayList<>();
            for (int i = 0; i < questions.size(); i++) {
                //获取对象
                JSONObject questionJson = questions.getJSONObject(i);
                QuestionImportVo questionImportVo = new QuestionImportVo();
                questionImportVo.setTitle(questionJson.getString("title"));
                questionImportVo.setType(questionJson.getString("type"));
                questionImportVo.setMulti(questionJson.getBoolean("multi"));
                questionImportVo.setDifficulty(questionJson.getString("difficulty"));
                questionImportVo.setScore(questionJson.getInteger("score"));
                questionImportVo.setAnalysis(questionJson.getString("analysis"));
                questionImportVo.setCategoryId(questionJson.getLong("categoryId"));
                    if("CHOICE".equals(questionImportVo.getType())) {
                        JSONArray choices = questionJson.getJSONArray("choices");
                        List<QuestionImportVo.ChoiceImportDto> choiceImportDtoList = new ArrayList<>(choices.size());
                        for (int j = 0; j < choices.size(); j++) {
                            JSONObject choicesJSONObject = choices.getJSONObject(j);
                            QuestionImportVo.ChoiceImportDto choiceImportDto = new QuestionImportVo.ChoiceImportDto();
                            choiceImportDto.setContent(choicesJSONObject.getString("content"));
                            choiceImportDto.setIsCorrect(choicesJSONObject.getBoolean("isCorrect"));
                            choiceImportDto.setSort(choicesJSONObject.getInteger("sort"));
                            choiceImportDtoList.add(choiceImportDto);
                        }
                        questionImportVo.setChoices(choiceImportDtoList);
                    }
                    //答案 判断题
                questionImportVo.setAnswer(questionJson.getString("answer"));
                    questionImportVoList.add(questionImportVo);
            }
            return questionImportVoList;
        }
        throw new RuntimeException("ai生成题目json数据结构错误，无法正常解析 数据为：%s".formatted(content));
    }
    @Override
    public String callKimiAi(String prompt) throws InterruptedException {
        //最多去进行三次重试
        int maxTry = 3;;
        for (int i = 1; i <= 3; i++) {
            try{
                Map<String,String> userMap = new HashMap<>();
                userMap.put("role","user");
                userMap.put("content",prompt);
                List<Map> messageList = new ArrayList<>();
                messageList.add(userMap);
                Map<String,Object> requestBody = new HashMap<>();
                requestBody.put("model",kimiProperties.getModel());
                requestBody.put("message",messageList);
                requestBody.put("temperature",kimiProperties.getTemperature());
                requestBody.put("max_tokens",kimiProperties.getMaxTokens());
                //发起网络请求调用
                Mono<String> stringMono = webClient.post()
                        .bodyValue(requestBody)
                        .retrieve()
                        .bodyToMono(String.class)
                        .timeout(Duration.ofSeconds(100));
                String result = stringMono.block();
                //Jackson 工具！ JsonObject JsonArray
                JSONObject resultJsonObject = JSONObject.parseObject(result);
                //错误结果:
                if(resultJsonObject.containsKey("error")){
                    throw new RuntimeException("访问错误了，错误信息为:" + resultJsonObject.getJSONObject("error").getString("message"));
                }
                String content = resultJsonObject.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content");
                if(content == null || content.isEmpty()){
                    throw new RuntimeException("调用成功！ 但是没有返回结果！！");
                }
                return content;
            } catch (Exception e){
                log.debug("第 {} 次 尝试调用失败了",i);
                Thread.sleep(1000);
                if( i == maxTry ){
                    e.printStackTrace();
                    throw new RuntimeException("已经重试3次！ 依然失败！ 请稍后再试！！");
                }
            }
        }
        throw new RuntimeException("已经重试 3 次！依然失败！ 请稍后再试试！！");
    }

}