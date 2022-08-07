package com.custom.postprocessing.scheduler;

import static com.custom.postprocessing.constant.PostProcessingConstant.ARCHIVE_DIRECTORY;
import static com.custom.postprocessing.constant.PostProcessingConstant.ARCHIVE_TEMP_DIRECTORY;
import static com.custom.postprocessing.constant.PostProcessingConstant.ARCHIVE_VALUE;
import static com.custom.postprocessing.constant.PostProcessingConstant.BACKSLASH_ASCII;
import static com.custom.postprocessing.constant.PostProcessingConstant.BANNER_DIRECTORY;
import static com.custom.postprocessing.constant.PostProcessingConstant.BANNER_PAGE;
import static com.custom.postprocessing.constant.PostProcessingConstant.EMPTY_SPACE;
import static com.custom.postprocessing.constant.PostProcessingConstant.FILE_SEPARATION;
import static com.custom.postprocessing.constant.PostProcessingConstant.LICENSE_DIRECTORY;
import static com.custom.postprocessing.constant.PostProcessingConstant.LICENSE_FILE_NAME;
import static com.custom.postprocessing.constant.PostProcessingConstant.LOG_DIRECTORY;
import static com.custom.postprocessing.constant.PostProcessingConstant.LOG_FILE;
import static com.custom.postprocessing.constant.PostProcessingConstant.OUTPUT_DIRECTORY;
import static com.custom.postprocessing.constant.PostProcessingConstant.PCL_EXTENSION;
import static com.custom.postprocessing.constant.PostProcessingConstant.PDF_EXTENSION;
import static com.custom.postprocessing.constant.PostProcessingConstant.PRINT_DIRECTORY;
import static com.custom.postprocessing.constant.PostProcessingConstant.PRINT_SUB_DIRECTORY;
import static com.custom.postprocessing.constant.PostProcessingConstant.PROCESS_DIRECTORY;
import static com.custom.postprocessing.constant.PostProcessingConstant.ROOT_DIRECTORY;
import static com.custom.postprocessing.constant.PostProcessingConstant.SPACE_VALUE;
import static com.custom.postprocessing.constant.PostProcessingConstant.TRANSIT_DIRECTORY;
import static com.custom.postprocessing.constant.PostProcessingConstant.XML_EXTENSION;
import static com.custom.postprocessing.constant.PostProcessingConstant.XML_TYPE;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.aspose.pdf.License;
import com.aspose.pdf.facades.PdfFileEditor;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobItem;
import com.custom.postprocessing.constant.PostProcessingConstant;
import com.custom.postprocessing.util.EmailUtility;
import com.custom.postprocessing.util.PostProcessUtil;
import com.custom.postprocessing.util.ZipUtility;
import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.blob.CloudBlob;
import com.microsoft.azure.storage.blob.CloudBlobClient;
import com.microsoft.azure.storage.blob.CloudBlobContainer;
import com.microsoft.azure.storage.blob.CloudBlobDirectory;
import com.microsoft.azure.storage.blob.CloudBlockBlob;
import com.microsoft.azure.storage.blob.ListBlobItem;

/**
 * @author kumar.charanswain
 *
 */

@Service
public class PostProcessingScheduler {

	public static final Logger logger = LoggerFactory.getLogger(PostProcessingScheduler.class);

	@Value("${blob.account.name.key}")
	private String connectionNameKey;

	@Value("${blob.container.name}")
	private String containerName;

	@Value("#{'${state.allow.type}'.split(',')}")
	private List<String> stateAllowType;

	@Value("#{'${page.type}'.split(',')}")
	private List<String> pageTypeList;

	@Value("${sheet.number.type}")
	private String sheetNbrType;

	@Value("${pcl.evaluation.copies}")
	private boolean pclEvaluationCopies;

	@Value("${empty.file.name}")
	private String blankPdfFileName;

	@Value("${memory.size}")
	private long memorySize;

	@Value("${empty.directory}")
	private String emptyDirectory;

	@Value("${zip.extension}")
	private String zipExtension;

	@Value("${backspace.value}")
	private String backSpaceValue;

	@Value("${status.message}")
	private String statusMessage;

	@Value("${archive.message}")
	private String archiveMessage;

	@Value("${archive.only}")
	private String archiveOnly;

	@Value("${print.only}")
	private String printOnly;

	@Value("${print.archive}")
	private String printArchive;

	@Value("${document.tag}")
	private String documentTag;

	@Value("${totalsheet.tag}")
	private String totalSheetTag;

	@Value("${claim.number}")
	private String dcnClaimNbr;

	@Value("${archive.temp.directory}")
	private String archiveTempDirectory;

	@Autowired
	EmailUtility emailUtility;

	@Autowired
	private PostProcessUtil postProcessUtil;

	List<String> pclFileList = new LinkedList<>();

	@Scheduled(cron = "${cron.job.print.interval}")
	public void postProcessing() {
		String message = smartComPostProcessing();
		logger.info(message);
	}

	public String smartComPostProcessing() {
		String currentDateTime = currentDateTimeStamp();
		String currentDate = currentDate();
		try {
			deletePreviousLogFile();
			final CloudBlobContainer container = containerinfo();
			Map<String, String> archiveMap = new ConcurrentHashMap<String, String>();
			archiveMap = moveFileToTargetDirectory(OUTPUT_DIRECTORY + ARCHIVE_DIRECTORY,
					OUTPUT_DIRECTORY + ARCHIVE_TEMP_DIRECTORY, container, currentDate, currentDateTime, archiveMap);
			moveTempFileToBackup(OUTPUT_DIRECTORY + ARCHIVE_TEMP_DIRECTORY,
					OUTPUT_DIRECTORY  + "temp-archive-" + currentDateTime + "/");
			moveTempFileTargetDirectory(OUTPUT_DIRECTORY + ARCHIVE_TEMP_DIRECTORY, OUTPUT_DIRECTORY + PRINT_DIRECTORY,
					container);
			String transitTargetDirectory = OUTPUT_DIRECTORY + TRANSIT_DIRECTORY + "/" + currentDate + "-"
					+ PROCESS_DIRECTORY + "/" + currentDateTime + PRINT_SUB_DIRECTORY + "/";
			archiveMap = moveFileToTargetDirectory(OUTPUT_DIRECTORY + PRINT_DIRECTORY, transitTargetDirectory,
					container, currentDate, currentDateTime, archiveMap);
			if (archiveMap.containsKey("nonarchive")) {
				CloudBlobDirectory printDirectory = getDirectoryName(container, OUTPUT_DIRECTORY,
						TRANSIT_DIRECTORY + "/" + currentDate() + "-" + PROCESS_DIRECTORY + "/" + currentDateTime
								+ PRINT_SUB_DIRECTORY + "/");
				statusMessage = processMetaDataInputFile(printDirectory, currentDateTime, currentDate);
			}

			if (archiveMap.size() == 0) {
				statusMessage = "no file for postprocessing";
			}
			String targetDirectory = OUTPUT_DIRECTORY + TRANSIT_DIRECTORY + backSpaceValue + currentDate + "/";
			archiveMessage = zipFileTransferToArchive(OUTPUT_DIRECTORY + ARCHIVE_TEMP_DIRECTORY, targetDirectory);
			logger.info(archiveMessage);

			String logFile = LOG_FILE + currentDate() + ".log";
			copyFileToTargetDirectory(logFile, ROOT_DIRECTORY, LOG_DIRECTORY);
		} catch (Exception exception) {
			logger.info("Exception smartComPostProcessing() " + exception.getMessage());
			statusMessage = "error in copy file to blob directory";
		}

		return statusMessage;
	}

	private boolean moveTempFileToBackup(String sourceDirectory, String targetDirectory) {
		boolean moveSuccess = false;
		BlobContainerClient blobContainerClient = getBlobContainerClient(connectionNameKey, containerName);
		Iterable<BlobItem> listBlobs = blobContainerClient.listBlobsByHierarchy(sourceDirectory);
		for (BlobItem blobItem : listBlobs) {
			String fileName = getFileName(blobItem.getName());
			fileName = findActualFileName(fileName);
			BlobClient dstBlobClient = blobContainerClient.getBlobClient(targetDirectory + fileName);
			BlobClient srcBlobClient = blobContainerClient.getBlobClient(blobItem.getName());
			String updateSrcUrl = srcBlobClient.getBlobUrl();
			if (srcBlobClient.getBlobUrl().contains(BACKSLASH_ASCII)) {
				updateSrcUrl = srcBlobClient.getBlobUrl().replace(BACKSLASH_ASCII, FILE_SEPARATION);
			}
			dstBlobClient.beginCopy(updateSrcUrl, null);
			//srcBlobClient.delete();
			moveSuccess = true;
		}
		return moveSuccess;
	}

	private void moveTempFileTargetDirectory(String sourceDirectory, String targetDirectory,
			CloudBlobContainer container) {
		BlobContainerClient blobContainerClient = getBlobContainerClient(connectionNameKey, containerName);
		Iterable<BlobItem> listBlobs = blobContainerClient.listBlobsByHierarchy(sourceDirectory);
		try {
			CloudBlobDirectory transitDirectory = getDirectoryName(container, OUTPUT_DIRECTORY + ARCHIVE_TEMP_DIRECTORY,
					emptyDirectory);
			for (BlobItem blobItem : listBlobs) {
				String fileName = getFileName(blobItem.getName());
				fileName = findActualFileName(fileName);
				BlobClient dstBlobClient = blobContainerClient.getBlobClient(targetDirectory + fileName);
				BlobClient srcBlobClient = blobContainerClient.getBlobClient(blobItem.getName());
				String updateSrcUrl = srcBlobClient.getBlobUrl();
				if (srcBlobClient.getBlobUrl().contains(BACKSLASH_ASCII)) {
					updateSrcUrl = srcBlobClient.getBlobUrl().replace(BACKSLASH_ASCII, FILE_SEPARATION);
				}
				dstBlobClient.beginCopy(updateSrcUrl, null);
				if (fileName.contains(archiveOnly)) {
					File printArchiveFile = new File(fileName);
					File updateFile = removeArchiveFileElement(printArchiveFile);
					CloudBlockBlob intialFileDownload = transitDirectory.getBlockBlobReference(fileName);
					intialFileDownload.downloadToFile(fileName);
					copyFileToTargetDirectory(updateFile.toString(), OUTPUT_DIRECTORY, ARCHIVE_TEMP_DIRECTORY);
					srcBlobClient.delete();
				} else if (fileName.contains(printArchive)) {
					File printArchiveFile = new File(fileName);
					File updateFile = removeArchiveFileElement(printArchiveFile);
					CloudBlockBlob intialFileDownload = transitDirectory.getBlockBlobReference(fileName);
					intialFileDownload.downloadToFile(fileName);
					copyFileToTargetDirectory(updateFile.toString(), OUTPUT_DIRECTORY, ARCHIVE_TEMP_DIRECTORY);
					srcBlobClient.delete();
					updateFile.delete();
				}
			}
		} catch (Exception exception) {
			logger.info("Exception moveTempFileTargetDirectory() :" + exception.getMessage());
		}
	}

	private Map<String, String> moveFileToTargetDirectory(String sourceDirectory, String targetDirectory,
			CloudBlobContainer container, String currentDate, String currentDateTime, Map<String, String> archiveMap) {
		try {
			BlobContainerClient blobContainerClient = getBlobContainerClient(connectionNameKey, containerName);
			Iterable<BlobItem> listBlobs = blobContainerClient.listBlobsByHierarchy(sourceDirectory);
			CloudBlobDirectory transitDirectory = getDirectoryName(container, "output", "print");
			if (sourceDirectory.contains("archive")) {
				transitDirectory = getDirectoryName(container, "output", "archive");
			}
			for (BlobItem blobItem : listBlobs) {
				String fileName = getFileName(blobItem.getName());
				fileName = findActualFileName(fileName);
				CloudBlockBlob intialFileDownload = transitDirectory.getBlockBlobReference(fileName);
				intialFileDownload.downloadToFile(fileName);
				BlobClient dstBlobClient = blobContainerClient.getBlobClient(targetDirectory + fileName);
				BlobClient srcBlobClient = blobContainerClient.getBlobClient(blobItem.getName());
				String updateSrcUrl = srcBlobClient.getBlobUrl();
				if (srcBlobClient.getBlobUrl().contains(BACKSLASH_ASCII)) {
					updateSrcUrl = srcBlobClient.getBlobUrl().replace(BACKSLASH_ASCII, FILE_SEPARATION);
				}
				if (targetDirectory.contains(archiveTempDirectory)) {
					dstBlobClient.beginCopy(updateSrcUrl, null);
					srcBlobClient.delete();
				} else if (sourceDirectory.contains(archiveTempDirectory)) {
					dstBlobClient.beginCopy(updateSrcUrl, null);
				} else if (fileName.contains(archiveOnly)) {
					File printArchiveFile = new File(fileName);
					File updateFile = removeArchiveFileElement(printArchiveFile);
					copyFileToTargetDirectory(updateFile.toString(), OUTPUT_DIRECTORY + TRANSIT_DIRECTORY + "/",
							currentDate + "-" + PROCESS_DIRECTORY + "/" + currentDateTime + "-" + ARCHIVE_DIRECTORY);
					srcBlobClient.delete();
					updateFile.delete();
					printArchiveFile.delete();
					archiveMap.put("archive", "true");
				} else if (fileName.contains(printOnly)) {
					CloudBlockBlob blob = transitDirectory.getBlockBlobReference(fileName);
					File updateFile = new File(fileName);
					blob.downloadToFile(updateFile.toString());
					dstBlobClient.beginCopy(updateSrcUrl, null);
					srcBlobClient.delete();
					// updateFile.delete();
					archiveMap.put("nonarchive", "true");
				} else if (fileName.contains(printArchive)) {
					File printArchiveFile = new File(fileName);
					File updateFile = removeArchiveFileElement(printArchiveFile);
					copyFileToTargetDirectory(updateFile.toString(), OUTPUT_DIRECTORY + TRANSIT_DIRECTORY + "/",
							currentDate + "-" + PROCESS_DIRECTORY + "/" + currentDateTime + "-" + ARCHIVE_DIRECTORY);
					CloudBlockBlob blob = transitDirectory.getBlockBlobReference(fileName);
					blob.downloadToFile(fileName);
					copyFileToTargetDirectory(fileName, OUTPUT_DIRECTORY + TRANSIT_DIRECTORY + "/",
							currentDate + "-" + PROCESS_DIRECTORY + "/" + currentDateTime + "-" + PRINT_DIRECTORY);
					srcBlobClient.delete();
					updateFile.delete();
					archiveMap.put("nonarchive", "true");
				} else {
					dstBlobClient.beginCopy(updateSrcUrl, null);
					srcBlobClient.delete();
					new File(fileName).delete();
					archiveMap.put("nonarchive", "true");
				}
			}
		} catch (Exception exception) {
			logger.info("Exception moveFileToTargetDirectory() :" + exception.getMessage());
		}
		return archiveMap;
	}

	public String processMetaDataInputFile(CloudBlobDirectory transitDirectory, String currentDateTime,
			String currentDate) {
		ConcurrentHashMap<String, List<String>> postProcessMap = new ConcurrentHashMap<>();
		try {
			Iterable<ListBlobItem> blobList = transitDirectory.listBlobs();

			for (ListBlobItem blobItem : blobList) {
				String fileName = getFileNameFromBlobURI(blobItem.getUri()).replace(SPACE_VALUE, EMPTY_SPACE);
				boolean stateType = checkStateType(fileName);
				if (stateType) {
					if (StringUtils.equalsIgnoreCase(FilenameUtils.getExtension(fileName), XML_TYPE)) {
						continue;
					}
					String fileNameNoExt = FilenameUtils.removeExtension(fileName);
					String[] stateAndSheetNameList = StringUtils.split(fileNameNoExt, "_");
					String stateAndSheetName = stateAndSheetNameList.length > 0
							? stateAndSheetNameList[stateAndSheetNameList.length - 1]
							: "";
					prepareMap(postProcessMap, stateAndSheetName, fileName);
				} else if (checkPageType(fileName)) {
					if (PostProcessingConstant.PDF_TYPE.equals(FilenameUtils.getExtension(fileName))) {
						continue;
					}
					prepareMap(postProcessMap, getSheetNumber(fileName, blobItem),
							StringUtils.replace(fileName, XML_EXTENSION, PDF_EXTENSION));
				} else {
					logger.info("unable to process:invalid document type");
				}
			}
			if (postProcessMap.size() > 0) {
				statusMessage = mergePDF(postProcessMap, currentDateTime, currentDate);
			} else {
				statusMessage = "no file for postprocessing";
			}
		} catch (Exception exception) {
			logger.info("Exception processMetaDataInputFile()" + exception.getMessage());
		}
		return statusMessage;
	}

	private String getSheetNumber(String fileName, ListBlobItem blobItem) {
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			// ET - added to mitigate vulnerability - Improper Restriction of XML External
			// Entity Reference CWE ID 611
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
			factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
			factory.setXIncludeAware(false);
			factory.setExpandEntityReferences(false);
			DocumentBuilder builder = factory.newDocumentBuilder();
			File file = new File(fileName);
			String xmlObjectString = getXMLAsString(fileName);
			if (!(xmlObjectString.contains("<totalSheet>"))) {
				logger.info("xml file doesn't conains totalSheet element tag:" + fileName);
				file.delete();
				return PostProcessingConstant.ZEROPAGE;
			}
			CloudBlob cloudBlob = (CloudBlob) blobItem;
			cloudBlob.downloadToFile(file.getPath());
			Document document = builder.parse(file);
			document.getDocumentElement().normalize();
			Element root = document.getDocumentElement();

			int sheetNumber = Integer.parseInt(root.getElementsByTagName(totalSheetTag).item(0).getTextContent());
			if (sheetNumber <= 10) {
				file.delete();
				return String.valueOf(sheetNumber);
			}
			file.delete();
		} catch (Exception exception) {
			logger.info("Exception getSheetNumber()" + exception.getMessage());
		}
		return PostProcessingConstant.MULTIPAGE;
	}

	public BlobContainerClient getBlobContainerClient(String connectionNameKey, String containerName) {
		BlobServiceClient blobServiceClient = new BlobServiceClientBuilder().connectionString(connectionNameKey)
				.buildClient();
		return blobServiceClient.getBlobContainerClient(containerName);
	}

	// post merge PDF
	public String mergePDF(ConcurrentHashMap<String, List<String>> postProcessMap, String currentDateTime,
			String currentDate) throws IOException {
		List<String> fileNameList = new LinkedList<>();
		CloudBlobContainer container = containerinfo();
		ConcurrentHashMap<String, List<String>> updatePostProcessMap = new ConcurrentHashMap<>();
		MemoryUsageSetting memoryUsageSetting = MemoryUsageSetting.setupMainMemoryOnly(memorySize);
		for (String fileType : postProcessMap.keySet()) {
			try {
				PDFMergerUtility pdfMerger = new PDFMergerUtility();
				fileNameList = postProcessMap.get(fileType);
				String bannerFileName = getBannerPage(fileType);
				logger.info("banner file is:" + bannerFileName);
				File bannerFile = new File(bannerFileName);
				String blankPage = getEmptyPage();
				pdfMerger.addSource(bannerFileName);
				pdfMerger.addSource(blankPage);
				Collections.sort(fileNameList);
				CloudBlobDirectory transitDirectory = getDirectoryName(container,
						OUTPUT_DIRECTORY + TRANSIT_DIRECTORY + "/",
						currentDate() + "-" + PROCESS_DIRECTORY + "/" + currentDateTime + PRINT_SUB_DIRECTORY + "/");
				try {
					for (String fileName : fileNameList) {
						File file = new File(fileName);
						logger.info("prcoess file is:" + fileName);
						CloudBlockBlob blob = transitDirectory.getBlockBlobReference(fileName);
						blob.downloadToFile(file.getAbsolutePath());
						pdfMerger.addSource(file.getPath());
					}
					fileType = postProcessUtil.getFileType(fileType);
					String currentDateTimeStamp = currentDateTimeStamp();
					String mergePdfFile = fileType + "_" + currentDateTimeStamp + PDF_EXTENSION;
					pdfMerger.setDestinationFileName(mergePdfFile);

					pdfMerger.mergeDocuments(memoryUsageSetting);

					statusMessage = convertPDFToPCL(mergePdfFile, container);
					updatePostProcessMap.put(fileType, fileNameList);
					// deleteFiles(pclFileList);
					bannerFile.delete();
					new File(mergePdfFile).delete();
					new File(blankPage).delete();
					deleteFiles(fileNameList);
				} catch (StorageException storageException) {
					logger.info("invalid file or may be banner file is missing");
					statusMessage = "invalid file or may be banner file is missing";
					if (fileNameList.size() > 0) {
						deleteFiles(fileNameList);
					}
					continue;
				} catch (Exception exception) {
					statusMessage = exception.getMessage();
					if (fileNameList.size() > 0) {
						deleteFiles(fileNameList);
					}
					logger.info("Exception mergePDF()" + exception.getMessage());
				}
			} catch (StorageException storageException) {
				logger.info("invalid file or may be banner file is missing");
				statusMessage = "invalid file or may be banner file is missing";
				if (fileNameList.size() > 0) {
					deleteFiles(fileNameList);
				}
				continue;
			} catch (Exception exception) {
				statusMessage = exception.getMessage();
				if (fileNameList.size() > 0) {
					deleteFiles(fileNameList);
				}
				logger.info("Exception mergePDF()" + exception.getMessage());
			}
		}

		if (postProcessMap.size() > 0) {
			emailUtility.emailProcess(pclFileList, currentDate, "PCL Creation process is completed successfully " + currentDate);
		}

		deleteFiles(pclFileList);

		File licenseFile = new File(LICENSE_FILE_NAME);
		licenseFile.delete();
		return statusMessage;
	}

	// post processing PDF to PCL conversion
	public String convertPDFToPCL(String mergePdfFile, CloudBlobContainer container) throws IOException {
		String outputPclFile = FilenameUtils.removeExtension(mergePdfFile) + PCL_EXTENSION;
		try {
			CloudBlobDirectory transitDirectory = getDirectoryName(container, ROOT_DIRECTORY, LICENSE_DIRECTORY);
			CloudBlockBlob blob = transitDirectory.getBlockBlobReference(LICENSE_FILE_NAME);
			String licenseFiles[] = blob.getName().split("/");
			String licenseFileName = licenseFiles[licenseFiles.length - 1];
			blob.downloadToFile(new File(licenseFileName).getAbsolutePath());
			License license = new License();
			license.setLicense(licenseFileName);
			statusMessage = pclFileCreation(mergePdfFile, outputPclFile);
		} catch (Exception exception) {
			statusMessage = "The license has expired:no need to print pcl file with evaluation copies";
		}
		if (pclEvaluationCopies) {
			statusMessage = "The license has expired:print pcl file with evaluation copies";
			pclFileCreation(mergePdfFile, outputPclFile);
		}
		return statusMessage;
	}

	public void copyFileToTargetDirectory(String fileName, String rootDirectory, String targetDirectory) {
		try {
			CloudBlobContainer container = containerinfo();
			CloudBlobDirectory processDirectory = getDirectoryName(container, rootDirectory, targetDirectory);
			File outputFileName = new File(fileName);
			if (outputFileName.exists()) {
				CloudBlockBlob processSubDirectoryBlob = processDirectory.getBlockBlobReference(fileName);
				final FileInputStream inputStream = new FileInputStream(outputFileName);
				processSubDirectoryBlob.upload(inputStream, outputFileName.length());
				inputStream.close();
			}

		} catch (Exception exception) {
			logger.info("Exception copyFileToTargetDirectory() " + exception.getMessage());
		}
	}

	public boolean checkStateType(String fileName) {
		for (String state : stateAllowType) {
			if (fileName.contains(state)) {
				return true;
			}
		}
		return false;
	}

	public boolean checkPageType(String fileName) {
		for (String pageType : pageTypeList) {
			if (fileName.contains(pageType)) {
				return true;
			}
		}
		return false;
	}

	public void deleteFiles(List<String> fileNameList) {
		for (String fileName : fileNameList) {
			File file = new File(fileName);
			file.delete();
		}
	}

	public void prepareMap(ConcurrentHashMap<String, List<String>> postProcessMap, String key, String fileName) {
		if (postProcessMap.containsKey(key)) {
			List<String> existingFileNameList = postProcessMap.get(key);
			existingFileNameList.add(fileName);
			postProcessMap.put(key, existingFileNameList);
		} else {
			List<String> existingFileNameList = new ArrayList<>();
			existingFileNameList.add(fileName);
			postProcessMap.put(key, existingFileNameList);
		}
	}

	public String getBannerPage(String key)
			throws URISyntaxException, StorageException, FileNotFoundException, IOException {
		CloudBlobContainer container = containerinfo();
		CloudBlobDirectory transitDirectory = getDirectoryName(container, ROOT_DIRECTORY, BANNER_DIRECTORY);
		String bannerFileName = BANNER_PAGE + key + PDF_EXTENSION;
		CloudBlockBlob blob = transitDirectory.getBlockBlobReference(bannerFileName);
		File source = new File(bannerFileName);
		blob.downloadToFile(source.getAbsolutePath());
		return bannerFileName;
	}

	public String getEmptyPage() throws URISyntaxException, StorageException, FileNotFoundException, IOException {
		CloudBlobContainer container = containerinfo();
		CloudBlobDirectory transitDirectory = getDirectoryName(container, ROOT_DIRECTORY, BANNER_DIRECTORY);
		String blankPage = blankPdfFileName + PDF_EXTENSION;
		CloudBlockBlob blob = transitDirectory.getBlockBlobReference(blankPage);
		File source = new File(blankPage);
		blob.downloadToFile(source.getAbsolutePath());
		return blankPage;
	}

	public CloudBlobContainer containerinfo() {
		CloudBlobContainer container = null;
		try {
			CloudStorageAccount account = CloudStorageAccount.parse(connectionNameKey);
			CloudBlobClient serviceClient = account.createCloudBlobClient();
			container = serviceClient.getContainerReference(containerName);
		} catch (Exception exception) {
			logger.info("Exception containerinfo() " + exception.getMessage());
		}
		return container;
	}

	public String currentDate() {
		Date date = new Date();
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
		return dateFormat.format(date);
	}

	public String currentDateTimeStamp() {
		Date date = new Date();
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss");
		return dateFormat.format(date);
	}

	public CloudBlobDirectory getDirectoryName(CloudBlobContainer container, String directoryName,
			String subDirectoryName) throws URISyntaxException {
		CloudBlobDirectory cloudBlobDirectory = container.getDirectoryReference(directoryName);
		if (StringUtils.isBlank(subDirectoryName)) {
			return cloudBlobDirectory;
		}
		return cloudBlobDirectory.getDirectoryReference(subDirectoryName);
	}

	private String getFileNameFromBlobURI(URI blobUri) {
		final String[] fileNameList = blobUri.toString().split(backSpaceValue);
		Optional<String> fileName = Optional.empty();
		if (fileNameList.length > 1)
			fileName = Optional.ofNullable(fileNameList[fileNameList.length - 1]);
		return fileName.get();
	}

	public void deletePreviousLogFile() {
		LocalDate date = LocalDate.now();
		LocalDate previousDate = date.minusDays(1);
		File previousDayLogFile = new File(LOG_FILE + previousDate + ".log");
		if (previousDayLogFile.exists()) {
			previousDayLogFile.delete();
		}
	}

	public String zipFileTransferToArchive(String rootDirectoryName, String targetDirectory) throws IOException {
		try {
			CloudBlobContainer container = containerinfo();
			BlobContainerClient blobContainerClient = getBlobContainerClient(connectionNameKey, containerName);
			CloudBlobDirectory transitDirectory = getDirectoryName(container, rootDirectoryName, emptyDirectory);
			String currentDateTime = currentDateTimeStamp();
			Iterable<BlobItem> listBlobs = blobContainerClient.listBlobsByHierarchy(rootDirectoryName);
			List<String> files = new LinkedList<String>();

			String archiveZipFileName = currentDateTime + "-" + ARCHIVE_VALUE + zipExtension;
			for (BlobItem blobItem : listBlobs) {
				String fileNames[] = StringUtils.split(blobItem.getName(), backSpaceValue);
				String fileName = fileNames[fileNames.length - 1];
				File file = new File(fileName);
				CloudBlockBlob blob = transitDirectory.getBlockBlobReference(fileName);
				blob.downloadToFile(file.getPath());
				files.add(fileName);
				blob.delete();
			}
			if (files.size() > 0) {
				ZipUtility zipUtility = new ZipUtility();
				zipUtility.zipProcessing(files, archiveZipFileName);
				copyFileToTargetDirectory(archiveZipFileName, emptyDirectory, targetDirectory);
				deleteFiles(files);
				new File(archiveZipFileName).delete();
			} else {
				archiveMessage = "no file for archive";
			}
		} catch (Exception exception) {
			archiveMessage = exception.getMessage();
			logger.info("zipFileTransferToArchive() exception:" + exception.getMessage());
		}
		logger.info("zipFileTransferToArchive status is :" + archiveMessage);
		return archiveMessage;
	}

	public String getFileName(String blobName) {
		return blobName.replace(OUTPUT_DIRECTORY, "");
	}

	public String findActualFileName(String fileName) {
		String updateFileName = "";
		if (StringUtils.isNoneEmpty(fileName)) {
			String fileNames[] = fileName.split("/");
			updateFileName = fileNames[fileNames.length - 1];
		}
		return updateFileName;
	}

	public String pclFileCreation(String mergePdfFile, String outputPclFile) {
		try {
			PdfFileEditor fileEditor = new PdfFileEditor();
			final InputStream stream = new FileInputStream(mergePdfFile);
			final InputStream[] streamList = new InputStream[] { stream };
			final OutputStream outStream = new FileOutputStream(outputPclFile);
			fileEditor.concatenate(streamList, outStream);
			stream.close();
			outStream.close();
			fileEditor.setCloseConcatenatedStreams(true);
			String currentDate = currentDate();
			copyFileToTargetDirectory(outputPclFile, OUTPUT_DIRECTORY + TRANSIT_DIRECTORY, currentDate);
			pclFileList.add(outputPclFile);
		} catch (Exception exception) {
			statusMessage = "error in pcl generate";
			logger.info("Exception pclFileCreation() " + exception.getMessage());
		}
		return statusMessage;
	}

	public File removeArchiveFileElement(File file) {
		File updateFile = null;
		try {
			if (PostProcessingConstant.PDF_TYPE.equals(FilenameUtils.getExtension(file.toString()))) {
				String[] splitFileName = file.toString().split("_");
				updateFile = new File(splitFileName[0] + ".pdf");
				file.renameTo(updateFile);
			} else if (PostProcessingConstant.XML_TYPE.equals(FilenameUtils.getExtension(file.toString()))) {
				DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
				// ET - added to mitigate vulnerability - Improper Restriction of XML External
				// Entity Reference CWE ID 611
				factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
				factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
				factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
				factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
				factory.setXIncludeAware(false);
				factory.setExpandEntityReferences(false);
				DocumentBuilder builder = factory.newDocumentBuilder();
				Document document = builder.parse(file);
				document.getDocumentElement().normalize();
				Element root = document.getDocumentElement();
				String claimNumber = "";

				Node node = document.getElementsByTagName(documentTag).item(0);
				NodeList list = node.getChildNodes();
				for (int i = 0; i < list.getLength(); i++) {
					Node documentNode = list.item(i);
					if (documentNode.getNodeName().equals(totalSheetTag)) {
						node.removeChild(documentNode);
					}
					if (documentNode.getNodeName().equals(dcnClaimNbr)) {
						claimNumber = root.getElementsByTagName(dcnClaimNbr).item(0).getTextContent();
					}
				}
				document.normalize();
				TransformerFactory tf = TransformerFactory.newInstance();
				Transformer transformer = tf.newTransformer();
				transformer.setOutputProperty(OutputKeys.INDENT, "yes");
				transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

				updateFile = new File(claimNumber + ".xml");
				DOMSource source = new DOMSource(document);
				StreamResult res = new StreamResult(file);
				transformer.transform(source, res);
				file.renameTo(updateFile);
			}

		} catch (TransformerException e) {
			e.printStackTrace();
		} catch (Exception exception) {
			logger.info("Exception archiveFileRemoveElement() " + exception.getMessage());
		}
		return updateFile;
	}

	private static String getXMLAsString(String strXMLFilePath) {

		StringBuilder stringBuilder = new StringBuilder();
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new FileReader(new File(strXMLFilePath)));
			String stringLine;
			while ((stringLine = reader.readLine()) != null) {
				stringBuilder.append(stringLine);
			}
		} catch (Exception exception) {
			exception.printStackTrace();
		} finally {
			try {
				if (reader != null)
					reader.close();
			} catch (Exception exception) {
				exception.printStackTrace();
			}
		}
		// convert to string
		return stringBuilder.toString();
	}

	public void mailTest() {
		String currentDate = currentDate();
		emailUtility.sendEmail(pclFileList, currentDate, "PCL Creation process is completed successfully " + currentDate);
	}
}
