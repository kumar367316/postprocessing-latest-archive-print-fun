package com.custom.postprocessing;

import static com.custom.postprocessing.constant.PostProcessingConstant.APPLICATION_PROPERTY_DIRECTORY;
import static com.custom.postprocessing.constant.PostProcessingConstant.PROPERTY_FILE_NAME;
import static com.custom.postprocessing.constant.PostProcessingConstant.ROOT_DIRECTORY;
import static com.custom.postprocessing.constant.PostProcessingConstant.ACCOUNT_KEY_VALUE;
import static com.custom.postprocessing.constant.PostProcessingConstant.CONTAINER_NAME;

import java.io.File;
import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.custom.postprocessing.scheduler.PostProcessingScheduler;
import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.blob.CloudBlobClient;
import com.microsoft.azure.storage.blob.CloudBlobContainer;
import com.microsoft.azure.storage.blob.CloudBlobDirectory;
import com.microsoft.azure.storage.blob.CloudBlockBlob;
import com.ulisesbocchio.jasyptspringboot.annotation.EnableEncryptableProperties;

/**
 * @author kumar.charanswain
 *
 */
@SpringBootApplication
@EnableScheduling
@EnableEncryptableProperties
public class PostProcessingApplication extends SpringBootServletInitializer {

	public static final Logger logger = LoggerFactory.getLogger(PostProcessingApplication.class);

	public static void main(String[] args) {

		new SpringApplicationBuilder(PostProcessingApplication.class).sources(PostProcessingApplication.class)
				.properties(getProperties()).run(args);
	}

	@Override
	protected SpringApplicationBuilder configure(SpringApplicationBuilder springApplicationBuilder) {
		return springApplicationBuilder.sources(PostProcessingApplication.class).properties(getProperties());
	}

	static Properties getProperties() {
		Properties props = new Properties();
		try {
			PostProcessingScheduler postProcessingScheduler = new PostProcessingScheduler();
			CloudBlobContainer container = containerInfo();
			CloudBlobDirectory transitDirectory = postProcessingScheduler.getDirectoryName(container, ROOT_DIRECTORY,
					APPLICATION_PROPERTY_DIRECTORY);
			CloudBlockBlob blob = transitDirectory.getBlockBlobReference(PROPERTY_FILE_NAME);
			String propertiesFiles[] = blob.getName().split("/");
			String propertyFileName = propertiesFiles[propertiesFiles.length - 1];
			File sourceFile = new File(propertyFileName);
			blob.downloadToFile(sourceFile.getAbsolutePath());
			props.put("spring.config.location", propertyFileName);
		} catch (Exception exception) {
			logger.info("exception getProperties() ", exception);
		}
		return props;
	}
	public static CloudBlobContainer containerInfo() throws InvalidKeyException, URISyntaxException, StorageException {
		CloudStorageAccount account = CloudStorageAccount.parse(ACCOUNT_KEY_VALUE);
		CloudBlobClient serviceClient = account.createCloudBlobClient();
		CloudBlobContainer container = serviceClient.getContainerReference(CONTAINER_NAME);
		return container;
	}
	static {
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
		System.setProperty("current.date.time", dateFormat.format(new Date()));
	}
}
