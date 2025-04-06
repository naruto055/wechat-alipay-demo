package com.naruto.model.vo;


import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * 统一结果返回类
 *
 * @Author: naruto
 * @CreateTime: 2025-04-05-21:25
 */
@Data
public class R {
    private Integer code;
    private String message;
    private Map<String, Object> data = new HashMap<>();

    public static R ok() {
        R r = new R();
        r.setCode(0);
        r.setMessage("成功");
        return r;
    }

    public static R error() {
        R r = new R();
        r.setCode(-1);
        r.setMessage("失败");
        return r;
    }

    public R data(String key, Object value) {
        this.data.put(key, value);
        return this;
    }
}
