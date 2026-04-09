package com.prodigal.travel.tools;

import com.prodigal.travel.tools.WebSearchTool;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

@Slf4j
@SpringBootTest
class WebSearchToolTest {
    @Value("${prodigal.search-api.api-key}")
    private String apiKey;


    @Test
    void searchWeb() {
        WebSearchTool webSearchTool = new WebSearchTool(apiKey);
        String result = webSearchTool.searchWeb("铜仁旅游景点推荐");
        log.info(result);
    }
}