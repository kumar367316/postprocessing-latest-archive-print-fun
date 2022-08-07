package com.custom.postprocessing.util;

import java.io.File;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

import javax.mail.BodyPart;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMessage.RecipientType;
import javax.mail.internet.MimeMultipart;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.custom.postprocessing.email.api.dto.MailRequest;
import com.custom.postprocessing.email.api.dto.MailResponse;

/**
 * @author kumar.charanswain
 *
 */

@Component
public class EmailUtility {

	public static final Logger logger = LoggerFactory.getLogger(EmailUtility.class);

	@Value("${mail.from}")
	private String mailForm;

	@Value("${mail.to}")
	private String mailTo;

	@Value("${mail.pcl.subject}")
	private String postProcessingSubject;

	@Value("${mail.smtp.starttls.key}")
	private String starttlsKey;

	@Value("${mail.smtp.starttls.value}")
	private String starttlsValue;

	@Value("${mail.smtp.host.key}")
	private String hostKey;

	@Value("${mail.smtp.host.value}")
	private String hostValue;

	@Value("${mail.smtp.port.key}")
	private String portKey;

	@Value("${mail.smtp.port.value}")
	private String portValue;

	@Value("${mail.smtp.auth.key}")
	private String authKey;

	@Value("${mail.smtp.auth.value}")
	private String authValue;

	@Value("${status.message}")
	private String statusMessage;

	public MailResponse sendEmail(MailRequest request, String currentDate) {
		MailResponse response = new MailResponse();
		try {
			Properties props = new Properties();
			props.put(starttlsKey, starttlsValue);
			props.put(hostKey, hostValue);
			props.put(portKey, portValue);
			props.put(authKey, authValue);
			Session session = Session.getDefaultInstance(props);
			MimeMessage message = new MimeMessage(session);
			message.setFrom(new InternetAddress(mailForm));
			message.setRecipient(RecipientType.TO, new InternetAddress(mailTo));
			message.setSubject(postProcessingSubject);
			StringBuilder builder = new StringBuilder();
			if (request.getPclFileNames().size() >= 1) {
				builder.append("SmartComm PostProcessing has completed successfully");
				builder.append("<html><body><div>Summary</div><br/>"
						+ "<table style='border:2px solid black'>process pcl file list<br/>");
				builder.append("<tr><th>PCL FILE NAME</th></tr>");
				for (String fileName : request.getPclFileNames()) {
					builder.append("<tr><td>" + fileName + "</td></tr>");
				}
				builder.append("</table></br></body></html>");
				builder.append("<br/>PostProcessing archive completed successfully for Date " + currentDate);
				message.setContent(builder.toString(), "text/html");
			} else {
				builder.append("<br/>PostProcessing archive completed successfully for Date " + currentDate);
				message.setContent(builder.toString(), "text/html");
			}
			Transport.send(message);

			response.setMessage("mail send successfully : " + request.getTo());
			response.setStatus(Boolean.TRUE);
		} catch (AddressException addressException) {
			logger.info("email address invalid sendEmail() " + addressException.getMessage());
			response.setMessage("mail sending failure");
			response.setStatus(Boolean.FALSE);
		} catch (MessagingException messagingException) {
			logger.info("message invalid sendEmail() :" + messagingException.getMessage());
			response.setMessage("mail sending failure");
			response.setStatus(Boolean.FALSE);
		} catch (Exception exception) {
			logger.info("exception sendEmail() :" + exception.getMessage());
			response.setMessage("mail sending failure");
			response.setStatus(Boolean.FALSE);
		}
		return response;
	}

	public MailResponse sendEmail(List<String> pclFiles, String currentDate, String mailStatusMessage) {
		MailResponse response = new MailResponse();
		try {
			Properties props = new Properties();
			props.put(starttlsKey, starttlsValue);
			props.put(hostKey, hostValue);
			props.put(portKey, portValue);
			props.put(authKey, authValue);
			Session session = Session.getDefaultInstance(props);
			MimeMessage message = new MimeMessage(session);
			message.setFrom(new InternetAddress(mailForm));
			message.setRecipient(RecipientType.TO, new InternetAddress(mailTo));
			message.setSubject(postProcessingSubject);
			message.setText(mailStatusMessage, "UTF-8");
			StringBuilder builder = new StringBuilder();
			builder.append("SmartComm PostProcessing has completed successfully");
			builder.append("<html><body><br/>" + "<table style='border:2px solid black'><br/>");
			for (String fileName : pclFiles) {
				builder.append("<tr><br/><td>");
				builder.append(fileName).append("</td><br/><td>");
				builder.append("</td><br/><td>");
			}
			builder.append("</table></br></body></html>");

			BodyPart messageBodyPart = new MimeBodyPart();
			messageBodyPart.setText(builder.toString());
			Multipart multipart = new MimeMultipart();
			messageBodyPart = new MimeBodyPart();

			File logFileName = new File("smartcompostprocessing" + "_" + currentDate + ".log");
			messageBodyPart.setFileName(logFileName.toString());
			multipart.addBodyPart(messageBodyPart);
			message.setContent(multipart);
			message.setSentDate(new Date());
			response.setFile(logFileName);
			response.setMessage("mail send successfully : " + mailTo);
			response.setStatus(Boolean.TRUE);
			Transport.send(message);
		} catch (AddressException addressException) {
			logger.info("email address invalid sendEmail() ", addressException);
			response.setMessage("mail sending failure");
			response.setStatus(Boolean.FALSE);
		} catch (MessagingException messagingException) {
			logger.info("message invalid sendEmail() ", messagingException);
			response.setMessage("mail sending failure");
			response.setStatus(Boolean.FALSE);
		} catch (Exception exception) {
			logger.info("exception sendEmail() ", exception);
			response.setMessage("mail sending failure");
			response.setStatus(Boolean.FALSE);
		}
		return response;
	}

	public void emailProcess(List<String> pclFileList, String currentDate, String mailStatusMessage) {
		try {
			MailRequest mailRequest = new MailRequest();
			mailRequest.setFrom(mailForm);
			mailRequest.setTo(mailTo);
			mailRequest.setMailStatusMessage(mailStatusMessage);
			mailRequest.setPclFileNames(pclFileList);

			MailResponse mailResponse = sendEmail(mailRequest, currentDate);
			if (Objects.nonNull(mailResponse.getFile()))
				mailResponse.getFile().delete();
		} catch (Exception exception) {
			logger.info("exception emailProcess():" + exception.getMessage());
		}
	}

	public void addFileNameList(List<String> fileNames, List<String> updateFileNames) {
		for (String fileName : fileNames) {
			updateFileNames.add(fileName);
		}
	}

}
