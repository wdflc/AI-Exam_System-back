package com.atguigu.exam.service.impl;


import com.atguigu.exam.entity.Category;
import com.atguigu.exam.entity.Question;
import com.atguigu.exam.mapper.CategoryMapper;
import com.atguigu.exam.mapper.QuestionMapper;
import com.atguigu.exam.service.CategoryService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.baomidou.mybatisplus.extension.toolkit.Db.count;
import static com.baomidou.mybatisplus.extension.toolkit.Db.getById;
import static com.baomidou.mybatisplus.extension.toolkit.Db.save;
import static com.baomidou.mybatisplus.extension.toolkit.Db.updateById;


@Slf4j
@Service
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper,Category> implements CategoryService {


    private final CategoryMapper categoryMapper;
    private final QuestionMapper questionMapper;

    public CategoryServiceImpl(CategoryMapper categoryMapper, QuestionMapper questionMapper) {
        this.categoryMapper = categoryMapper;
        this.questionMapper = questionMapper;
    }

    @Override
    public List<Category> getAllCategories() {
        List<Category> categories = categoryMapper.selectList(new LambdaQueryWrapper<Category>().orderByAsc(Category::getSort));
        fillQuestionCount(categories);
        return categories;
    }

    @Override
    public List<Category> getCategoryTree() {
        //获取所有分类按照Sort排序
        List<Category> categories = categoryMapper.selectList(new LambdaQueryWrapper<Category>().orderByAsc(Category::getSort));
        //为每个分类填充其自身的题目数量
        fillQuestionCount(categories);
        //构建树形结构并返回
        List<Category> buildTree = buildTree(categories);
        log.info("buildTree:{}", buildTree);
        return buildTree;
    }

    @Override
    public void addCategory(Category category) {
        LambdaQueryWrapper<Category> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Category::getParentId, category.getParentId());
        queryWrapper.eq(Category::getName, category.getName());
        long count = count(queryWrapper);
        //说明添加过已经存在的了 我们不可以再去添加
        if(count > 0) {
            Category parent = getById(category.getParentId());
            throw new RuntimeException("在%s父分类下,已经存在名为:%s的子分类，本次添加失败".formatted(parent.getName(),category.getName()));
        }
        save(category);
    }

    @Override
    public void updateCategory(Category category) {
        //依旧校验
        LambdaQueryWrapper<Category> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Category::getParentId,category.getParentId());
        queryWrapper.ne(Category::getId, category.getId());
        queryWrapper.eq(Category::getName, category.getName());
        CategoryMapper categoryMapper = getBaseMapper();
        boolean exists = categoryMapper.exists(queryWrapper);
        if(exists) {
            Category parent = getById(category.getParentId());
            throw new RuntimeException("在%s父分类下，已经存在名为：%s的子分类，本次更新失败"
                    .formatted(parent.getName(),category.getName()));
        }
        updateById(category);
    }

    @Override
    public void deleteCategory(Long id) {
        //检查是否为 一级标题
        Category category = getById(id);
        if(category.getParentId() == 0){
            throw new RuntimeException("不能删除一级标题!");
        }
        //检查是否存在关联的题目
        LambdaQueryWrapper<Question> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Question::getCategoryId,id);
        long count = questionMapper.selectCount(queryWrapper);
        if(count > 0){
            throw new RuntimeException("当前的：%s分类，关联了%s道题目，无法删除！".formatted(category.getName(),count));
        }
        removeById(id);

    }

    private List<Category> buildTree(List<Category> categories) {
        //根据ParentId分组categoryMap
        Map<Long, List<Category>> categoryMap = categories.stream()
                .collect(Collectors.groupingBy(Category::getParentId));
        //遍历所有分类 为他们设置children属性 递归地累题目数量
        categories.forEach(category ->{
            List<Category> children = categoryMap.getOrDefault(category.getId(),new ArrayList<>());
            category.setChildren(children);
            long childrenQuestionCount = children.stream()
                    .mapToLong(c-> c.getCount()!=null?c.getCount():0).sum();
            long selfQuestionCount = category.getCount() != null ? category.getCount() : 0L;
            category.setCount(selfQuestionCount + childrenQuestionCount);
        });
        return categories.stream()
                .filter(c-> c.getParentId() == 0)
                .collect(Collectors.toList());
    }

    private void fillQuestionCount(List<Category> categories) {
        //1. 一次查出所有分类的 题目数量
        List<Map<Long,Object>> questionCountList = questionMapper.getCategoryQuestionCount();

        //将List<Map> 转换为Map<categoryId,count> 便于快速查找
        Map<Long,Long> questionCountMap = questionCountList.stream().collect(Collectors.toMap(
                map-> Long.valueOf(map.get("category_id").toString()),
                map-> Long.valueOf(map.get("count").toString())
        ));
        categories.forEach(category -> {
            category.setCount(questionCountMap.getOrDefault(category.getId(),0L));
        });
    }
}