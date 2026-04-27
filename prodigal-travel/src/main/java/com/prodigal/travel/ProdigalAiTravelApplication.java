package com.prodigal.travel;

import com.prodigal.travel.config.properties.AuthRegisterProperties;
import com.prodigal.travel.config.properties.AiAlertProperties;
import com.prodigal.travel.config.properties.JwtProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@MapperScan("com.prodigal.travel.mapper")
@EnableConfigurationProperties({JwtProperties.class, AuthRegisterProperties.class, AiAlertProperties.class})
public class ProdigalAiTravelApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProdigalAiTravelApplication.class, args);
        System.out.println("(♥◠‿◠)ﾉﾞ  prodigal-ai-travel👉模块启动成功   ლ(´ڡ`ლ)ﾞ  \n" +
                "oooooooooo oooooooooo    ooooooo  ooooooooo  ooooo  ooooooo8      o      ooooo       \n" +
                " 888    888 888    888 o888   888o 888    88o 888 o888    88     888      888        \n" +
                " 888oooo88  888oooo88  888     888 888    888 888 888    oooo   8  88     888        \n" +
                " 888        888  88o   888o   o888 888    888 888 888o    88   8oooo88    888      o \n" +
                "o888o      o888o  88o8   88ooo88  o888ooo88  o888o 888ooo888 o88o  o888o o888ooooo88 ");
    }

}
