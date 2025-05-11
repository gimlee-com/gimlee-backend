package com.gimlee.notifications.email

import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Service
import jakarta.mail.MessagingException

@Service
class EmailService(
    private val javaMailSender: JavaMailSender,
    @Value("\${spring.mail.properties.from:}")
    private val from: String,
    @Value("\${spring.mail.properties.reply-to:}")
    private val replyTo: String
) {
    fun sendEmail(to: String, title: String, content: String) {
        val mail = javaMailSender.createMimeMessage()
        try {
            val helper = MimeMessageHelper(mail, true)
            helper.setTo(to)
            helper.setReplyTo(replyTo)
            helper.setFrom(from)
            helper.setSubject(title)
            helper.setText(content, true)

        } catch (e: MessagingException) {
            throw EmailException("Unable to send email", e)
        }

        javaMailSender.send(mail)
    }
}
