package com.atguigu.exam.service.impl;

import com.atguigu.exam.common.CacheConstants;
import com.atguigu.exam.entity.Question;
import com.atguigu.exam.entity.QuestionAnswer;
import com.atguigu.exam.entity.QuestionChoice;
import com.atguigu.exam.mapper.QuestionAnswerMapper;
import com.atguigu.exam.mapper.QuestionChoiceMapper;
import com.atguigu.exam.mapper.QuestionMapper;
import com.atguigu.exam.service.QuestionService;
import com.atguigu.exam.utils.RedisUtils;
import com.atguigu.exam.vo.QuestionQueryVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 题目Service实现类
 * 实现题目相关的业务逻辑
 */
@Slf4j
@Service
public class QuestionServiceImpl extends ServiceImpl<QuestionMapper, Question> implements QuestionService {


    private final QuestionMapper questionMapper;
    private final QuestionChoiceMapper questionChoiceMapper;
    private final QuestionAnswerMapper questionAnswerMapper;

    public QuestionServiceImpl(QuestionMapper questionMapper, QuestionChoiceMapper questionChoiceMapper, QuestionAnswerMapper questionAnswerMapper) {
        this.questionMapper = questionMapper;
        this.questionChoiceMapper = questionChoiceMapper;
        this.questionAnswerMapper = questionAnswerMapper;
    }

    @Override
    public void customPageService(Page<Question> pageBean, QuestionQueryVo questionQueryVo) {
        questionMapper.customPage(pageBean,questionQueryVo);
    }

    @Override
    public void customPageJavaService(Page<Question> pageBean, QuestionQueryVo questionQueryVo) {
        LambdaQueryWrapper<Question> queryWrapper = new LambdaQueryWrapper<>();
        //判断的条件 !ObjectUtils.isEmpty(questionQueryVo.getType())(类似于xml文件里面的 <if>标签)
        queryWrapper.eq(!ObjectUtils.isEmpty(questionQueryVo.getType()),Question::getType,questionQueryVo.getType())    ;
        queryWrapper.eq(!ObjectUtils.isEmpty(questionQueryVo.getDifficulty()),Question::getDifficulty,questionQueryVo.getDifficulty());
        queryWrapper.eq(!ObjectUtils.isEmpty(questionQueryVo.getCategoryId()),Question::getCategoryId,questionQueryVo.getCategoryId());
        queryWrapper.like(!ObjectUtils.isEmpty(questionQueryVo.getKeyword()),Question::getTitle,questionQueryVo.getKeyword());
        queryWrapper.orderByDesc(Question::getCreateTime);
        page(pageBean,queryWrapper);
        fillQuestionCHoiceAndAnswer(pageBean.getRecords());
    }

    @Override
    public Question customDetailQuestion(Long id) {
        Question question = questionMapper.customGetById(id);
        if(question == null){
            throw new RuntimeException("题目查询详情失败！原因可能是提前被删除！ 题目id为:"+id);
        }
        new Thread(()->{
            increamentQuestion(question.getId());
        }).start();
        return question;

    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void customSaveQuestion(Question question) {
        //1. 一定插入题目信息 回显题目的ID
        //同一个类型不能够题目title相同
        LambdaQueryWrapper<Question> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Question::getType,question.getType());
        queryWrapper.eq(Question::getTitle,question.getTitle());
        boolean exists = baseMapper.exists(queryWrapper);
        if(exists){
            throw new RuntimeException("在%s下，存在%s名称的题目已经存在！，保存失败".formatted(question.getType(),question.getTitle()));
        }
        boolean saved = save(question);
        if(!saved){
            throw new RuntimeException("在%s下，存在%s名称的题目！ 保存失败！".formatted(question.getType(),question.getTitle()));
        }
        //2获取答案对象，并配置题目的id
        QuestionAnswer answer = question.getAnswer();
        answer.setQuestionId(question.getId());
        //判断是不是选择题
        if("CHOICE".equals(question.getType())){
            //如果是选择题的话 需要去判断是否正确
            List<QuestionChoice> choices = question.getChoices();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < choices.size(); i++) {
                QuestionChoice choice = choices.get(i);
                choice.setSort(i);
                choice.setQuestionId(question.getId());
                questionChoiceMapper.insert(choice);
                if(choice.getIsCorrect()){
                    if(sb.length()>0){
                        sb.append(",");
                    }
                    sb.append((char)('A'+i));
                }
            }
            //进行答案的赋值
            answer.setAnswer(sb.toString());
        }
        //保存答案对象
        questionAnswerMapper.insert(answer);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void customUpdateQuestion(Question question) {
        LambdaQueryWrapper<Question> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Question::getTitle,question.getTitle());
        queryWrapper.ne(Question::getId,question.getId());
        boolean exists = baseMapper.exists(queryWrapper);
        if(exists){
            throw new RuntimeException("修改：%s题目的新标题：%s和其他的题目重复了！ 修改失败！"
                    .formatted(question.getId(),question.getTitle()));
        }
        boolean updated = updateById(question);
        if(!updated){
            throw new RuntimeException("修改: %s题目失败！！"
                    .formatted(question.getId()));
        }
        //获取答案对象
        QuestionAnswer answer = question.getAnswer();
        //判断是否是选择题
        if("CHOICE".equals(question.getType())){
            List<QuestionChoice> choices = question.getChoices();
            LambdaQueryWrapper<QuestionChoice> lambdaQueryWrapper = new LambdaQueryWrapper<>();
            lambdaQueryWrapper.eq(QuestionChoice::getQuestionId,question.getId());
            questionChoiceMapper.delete(lambdaQueryWrapper);
            //循环新增的选项（选项上id==null）
            //拼接正确的档案 a,b
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < choices.size(); i++) {
                QuestionChoice choice = choices.get(i);
                choice.setId(null);
                choice.setSort(i);
                choice.setCreateTime(null);
                choice.setUpdateTime(null);
                choice.setQuestionId(question.getId());
                questionChoiceMapper.insert(choice);
                if(choice.getIsCorrect()){
                    if(sb.length()>0){
                        sb.append(",");
                    }
                    sb.append((char)('A'+i));
                }
            }
            answer.setAnswer(sb.toString());
        }
        questionAnswerMapper.updateById(answer);
    }

    @Override
    public void customRemoveQuestionById(Long id) {
        //判断试卷题目表，存在删除失败！
        LambdaQueryWrapper<Question> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Question::getId,id);
        Long count = baseMapper.selectCount(queryWrapper);
        if(count > 0){
            throw new RuntimeException("该题目： %s 被试卷表中饮用%s次，删除失败！".formatted(id,count));
        }
        // 删除主表 题目表
        boolean removed = removeById(id);
        if(!removed){
            throw new RuntimeException("该题目： %s 信息删除失败！！");
        }
        // 3. 删除子表 答案 以及选项表
        questionAnswerMapper.delete(new LambdaQueryWrapper<QuestionAnswer>().eq(QuestionAnswer::getQuestionId,id));
        questionChoiceMapper.delete(new LambdaQueryWrapper<QuestionChoice>().eq(QuestionChoice::getQuestionId,id));
    }

    @Override
    public List<Question> customFindPopularQuestions(Integer size) {
       List<Question> popularQuestions = new ArrayList<>();
       //去zset中后去热门的题目 并且添加到总集合当中
       //获取题目排行 需要获取id以及分数 分数用于后续的排序处理
        Set<ZSetOperations.TypedTuple<Object>> tupleSet = RedisUtils.zReverseRangeWithScores(CacheConstants.POPULAR_QUESTIONS_KEY, 0, size - 1);
        List<Long> idsSet = new ArrayList<>();
        if(tupleSet != null&&tupleSet.size()>0){
            //根据排行榜的 积分 倒序进行一个id的查询
            List<Long> idsList = tupleSet.stream()
                    .sorted((o1,o2)->Integer.compare(o2.getScore().intValue(),o1.getScore().intValue()))
                    .map(o-> Long.valueOf(o.getValue().toString())).collect(Collectors.toList());
            idsSet.addAll(idsList);
            log.debug("从redis获取热门题目的id集合，且保证顺序{}",idsList);
            for(Long id:idsSet){
                Question question = getById(id);
                if(question != null){
                    popularQuestions.add(question);
                }
            }
            log.debug("去redis查询的热门题目，数目为:{},题目的内容为：{}",popularQuestions.size(),popularQuestions);
                }
        int diff = size - popularQuestions.size();
        if(diff > 0){
            //4 不满足，题目表中 非热门题目 时间倒序 limit 差数量
            LambdaQueryWrapper<Question> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.notIn(Question::getId,idsSet);
            queryWrapper.orderByDesc(Question::getScore);
            queryWrapper.last("limit "+diff);
            List<Question> questionDiffList = list(queryWrapper);
            log.debug("去question表中补充热门题目，题目数:{},题目内容为：{}",questionDiffList.size(),questionDiffList);
            if(questionDiffList!=null && questionDiffList.size()>0){
                popularQuestions.addAll(questionDiffList);
            }
        }
        fillQuestionCHoiceAndAnswer(popularQuestions);
        return popularQuestions;
    }

    private void increamentQuestion(Long id) {
        Double score  = RedisUtils.zIncrementScore(CacheConstants.POPULAR_QUESTIONS_KEY,id,1);
        log.info("完成{}题目分数累计，累计后分数为：{}",id,score);
    }

    private void fillQuestionCHoiceAndAnswer(List<Question> questionList) {
        if(questionList == null|| questionList.size()==0){
            log.debug("没有查询对应的问题集合数据");
            return;
        }
        List<Long> ids = questionList.stream().map(Question::getId).collect(Collectors.toList());
        List<QuestionChoice> questionChoiceList = questionChoiceMapper.selectList(new LambdaQueryWrapper<QuestionChoice>().in(QuestionChoice::getQuestionId,ids));
        List<QuestionAnswer> questionAnswers = questionAnswerMapper.selectList(new LambdaQueryWrapper<QuestionAnswer>().in(QuestionAnswer::getQuestionId,ids));
        Map<Long,List<QuestionChoice>> questionChoiceMap = questionChoiceList.stream().collect(Collectors.groupingBy(QuestionChoice::getQuestionId));
        Map<Long, QuestionAnswer> answerMap = questionAnswers.stream().collect(Collectors.toMap(QuestionAnswer::getQuestionId, a -> a));
        questionList.forEach(question ->{
            question.setAnswer(answerMap.get(question.getId()));
            if("CHOICE".equals(question.getType())){
                List<QuestionChoice> questionChoices = questionChoiceMap.get(question.getId());
                questionChoices.sort(Comparator.comparingInt(QuestionChoice::getSort));
                question.setChoices(questionChoices);
            }
        });
    }
}