package sm.core.utils;

import jakarta.mail.internet.MimeMessage;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import org.springframework.stereotype.Service;

@Service
public class EmailService {



    @Autowired
    private JavaMailSender mailSender;

    public boolean enviarEmailHtml(String destinatario, String assunto, String htmlCorpo) {
        try {
            MimeMessage mensagem = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mensagem, true, "UTF-8");

            helper.setTo(destinatario);
            helper.setSubject(assunto);
            helper.setText(htmlCorpo, true); // true => HTML
            helper.setFrom("geral@sm.com.pt", "Equipa SM");

            mailSender.send(mensagem);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

   

}