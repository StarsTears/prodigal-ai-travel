package com.prodigal.travel.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @project prodigal-ai-travel
 * @author Lang
 * @since  2026/4/1
 * @Version: 1.0
 * @description 项目健康检测
 */
@RestController
@RequestMapping("health")
public class HealthController {
    @PostMapping("/check")
    public String check() {
        return "OK";
    }

}
