package com.prodigal.travel.agent;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;


@Slf4j
@SpringBootTest
class ProdigalManusTest {
    @Resource
    ProdigalManus prodigalManus;

    @Test
    public void run(){
        String prompt = "给我几张海上大桥的照片";
        String result = prodigalManus.run(prompt);
        Assertions.assertNotNull( result);
    }


}