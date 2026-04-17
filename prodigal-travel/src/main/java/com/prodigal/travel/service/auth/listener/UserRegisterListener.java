package com.prodigal.travel.service.auth.listener;

import com.prodigal.travel.config.rabbitmq.UserRabbiMQConfig;
import com.prodigal.travel.exception.ResponseStatus;
import com.prodigal.travel.exception.ThrowUtils;
import com.prodigal.travel.model.dto.MailContentDTO;
import com.prodigal.travel.model.entity.User;
import com.prodigal.travel.model.event.UserEvent;
import com.prodigal.travel.service.auth.MailService;
import com.prodigal.travel.service.auth.UserService;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * @author Lang
 * @project prodigal-ai-travel
 * @Version: 1.0
 * @description 用户注册监听
 * @since 2026/4/15
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserRegisterListener {
    private final MailService mailService;
    private final UserService userService;

    @RabbitListener(queues = UserRabbiMQConfig.USER_REGISTERED_EMAIL_QUEUE,
            containerFactory = "userRabbitListenerContainerFactory")
    public void onUserRegistered(UserEvent message) {
        log.info("收到用户注册消息 userId={}", message.getId());

        // 先查询数据库
        User user = userService.getById(message.getId());
        ThrowUtils.throwIf(user == null, ResponseStatus.USER_NOT_FOUND);
        // 构建邮件内容
        MailContentDTO mailContentDTO = MailContentDTO.builder()
                .subject("\uD83C\uDF89 欢迎加入AI旅游助手 - 注册成功")
                .to(user.getEmail())
                .content(this.buildHtmlContent(user))
                .isHtml(true)
                .build();
        // 发送邮件
        try {
            mailService.sendEmail(mailContentDTO);
            log.info("成功发送 welcome 邮件 userId={}、to={}", message.getId(), user.getEmail());
        } catch (MessagingException e) {
            log.error("发送邮件失败", e);
            throw new RuntimeException(e);
        }
    }

    // 构建邮件内容（HTML格式）
    private String buildHtmlContent(User user) {

        return "<!DOCTYPE html>" +
                "<html>" +
                "<head>" +
                "<meta charset='UTF-8'>" +
                "<style>" +
                "body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }" +
                ".container { max-width: 600px; margin: 0 auto; padding: 20px; }" +
                ".header { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 30px; text-align: center; border-radius: 10px 10px 0 0; }" +
                ".content { background: #f9f9f9; padding: 30px; border-radius: 0 0 10px 10px; }" +
                ".feature { margin: 15px 0; padding: 10px; background: white; border-left: 4px solid #667eea; }" +
                ".footer { text-align: center; margin-top: 20px; color: #999; font-size: 12px; }" +
                ".button { display: inline-block; padding: 12px 24px; background: #667eea; color: white; text-decoration: none; border-radius: 5px; margin: 20px 0; }" +
                "</style>" +
                "</head>" +
                "<body>" +
                "<div class='container'>" +
                "<div class='header'>" +
                "<h1>🎉 欢迎加入AI旅游助手</h1>" +
                "</div>" +
                "<div class='content'>" +
                "<p>你好，<strong>" + user.getUsername() + "</strong>！</p>" +
                "<p>感谢你注册AI旅游助手，我们很高兴能成为你旅行路上的智能伙伴！</p>" +

                "<h3>✨ 你可以使用以下功能：</h3>" +
                "<div class='feature'>🤖 <strong>智能行程规划</strong> - AI帮你定制专属旅行路线</div>" +
                "<div class='feature'>📍 <strong>景点推荐</strong> - 发现你不知道的精彩目的地</div>" +
                "<div class='feature'>📚 <strong>实时攻略</strong> - 获取当地吃喝玩乐实用信息</div>" +
                "<div class='feature'>💰 <strong>预算管理</strong> - 轻松掌控旅行开支</div>" +

                "<div style='text-align: center;'>" +
                "<a href='https://your-website.com' class='button'>立即开始探索 →</a>" +
                "</div>" +

                "<p>如有任何问题，欢迎随时联系我们的客服团队。</p>" +

                "<p>祝您旅途愉快！</p>" +
                "<p>—— AI旅游助手团队</p>" +
                "</div>" +
                "<div class='footer'>" +
                "<p>此邮件为系统自动发送，请勿直接回复</p>" +
                "<p>© 2026 AI旅游助手 保留所有权利</p>" +
                "</div>" +
                "</div>" +
                "</body>" +
                "</html>";
    }
}
