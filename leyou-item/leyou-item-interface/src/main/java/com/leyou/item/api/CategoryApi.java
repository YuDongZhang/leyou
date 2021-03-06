package com.leyou.item.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;


@RequestMapping("category")
public interface CategoryApi {

    @GetMapping//这里和老师讲的不一样
    public List<String> queryNamesByIds(@RequestParam("ids") List<Long> ids);
}