package com.lams.api.service.impl;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.StringTokenizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lams.api.domain.DocumentMappingMstr;
import com.lams.api.domain.DocumentUserMappingMstr;
import com.lams.api.repository.ApplicationsRepository;
import com.lams.api.repository.DocumentMappingMstrRepository;
import com.lams.api.repository.DocumentUserMappingMstrRepository;
import com.lams.api.repository.UserMstrRepository;
import com.lams.api.service.DocumentUploadService;
import com.lams.model.bo.DocumentRequest;
import com.lams.model.bo.DocumentResponse;
import com.lams.model.utils.CommonUtils;
import com.lams.model.utils.CommonUtils.DocumentType;

@Service
@Transactional
public class DocumentUploadServiceImpl implements DocumentUploadService {

	private static final Logger logger = LoggerFactory.getLogger(DocumentUploadServiceImpl.class);
	
	@Autowired
	private DocumentMappingMstrRepository documentMappingMstrRepository;
	
	@Autowired
	private DocumentUserMappingMstrRepository documentUserMappingMstrRepository;
	
	@Autowired
	private UserMstrRepository userMstrRepository;
	
	@Autowired
	private ApplicationsRepository applicationsRepository;

	@Autowired
	private Environment environment;
	
	private static final String SERVER_PATH = "com.lams.server.path";

	
	@Override
	public DocumentResponse upload(String fileName, byte[] bytes, DocumentRequest documentRequest) {
		
		String originalName = fileName;
		String localPath = System.getProperty("user.dir") + "/src/main/resources/static/image/";
		DocumentResponse documentResponse = new DocumentResponse();
		fileName = encryptFileName(fileName);
        String storeFileName = null;
        String extension = null;
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd-HH:mm:ss");
        StringTokenizer tokenizer = new StringTokenizer(fileName,".");
        File file = new File(localPath);
        if (!file.exists()) {
            System.out.println(file.mkdirs());
        }
        storeFileName = tokenizer.nextToken();
        extension = tokenizer.nextToken();
        String date = format.format(new Date()).trim();
        date = date.replaceAll("-", "_");
        date = date.replaceAll(":", "-");
        date = date.trim();
        storeFileName += "_" + date + "." + extension;
        storeFileName = storeFileName.trim();
        try {
            Path path = Paths.get(localPath + storeFileName);
            Files.write(path, bytes);
            documentResponse.setIsFileUpload(true);
            String storedFilePath = environment.getRequiredProperty(SERVER_PATH) + "image/" + storeFileName;
            documentResponse.setFilePath(storedFilePath);
            documentResponse.setOriginalName(originalName);
            if(!CommonUtils.isObjectNullOrEmpty(documentRequest.getIsUserDocument()) && documentRequest.getIsUserDocument()) {
            	documentResponse.setDocumentMappingId(updateUserDocMappingTable(documentRequest.getDocumentId(), documentRequest.getUserId(), storedFilePath,originalName));
            } else {
            	documentResponse.setDocumentMappingId(updateDocMappingTable(documentRequest.getDocumentId(), documentRequest.getApplicationId(), documentRequest.getUserId(), storedFilePath,originalName));	
            }
            logger.info("file saved in local! {}");
        } catch (IOException e) {
            logger.error("file not saved in local! because : ->", e);
            documentResponse.setIsFileUpload(false);
            e.printStackTrace();
        }
        logger.info("Exit with saveFileOnLocal() {}");
		return documentResponse;	
	}
	@Override
	public DocumentResponse getUserDocumentByDocId(Long userId, Long documentMstrId) {
		
		DocumentResponse documentResponse = new DocumentResponse();
		
		DocumentUserMappingMstr documentUserMappingMstr = documentUserMappingMstrRepository.getByUserIdAndDocId(userId, documentMstrId);
		if(!CommonUtils.isObjectNullOrEmpty(documentUserMappingMstr)) {
			documentResponse.setDocumentMappingId(documentUserMappingMstr.getId());
			documentResponse.setDocumentMstrId(documentUserMappingMstr.getDocumentMstrId());
			documentResponse.setFilePath(documentUserMappingMstr.getFilePath());
			documentResponse.setOriginalName(documentUserMappingMstr.getOriginalName());
		}
		
		return documentResponse;
		
	}
	
	
	@Override
	public List<DocumentResponse> getDocumentList(Long applicationId,List<Long> docId){
		
		Long userId = applicationsRepository.getUserIdByAppId(applicationId);
		Long employmentType = userMstrRepository.getEmpTypeById(userId);
		
		if(CommonUtils.isObjectNullOrEmpty(employmentType)) {
			logger.info("Employment Type is null or Empty !!");
			return Collections.emptyList();
		}
		
		List<DocumentType> all = DocumentType.getAllByEmpType(employmentType);
		List<DocumentResponse> docResponseList = new ArrayList<>(all.size());
		DocumentResponse documentResponse = null;
		for(DocumentType documentType : all) {
			documentResponse = new DocumentResponse();
			documentResponse.setDocumentMstrId(documentType.getId());
			documentResponse.setDocumentMstrName(documentType.getName());
			List<DocumentMappingMstr> documentMappingMstrList = documentMappingMstrRepository.getDocumentByAppIdAndDocMstId(applicationId, documentType.getId());
			List<DocumentResponse> docummentResponseList = new ArrayList<>(documentMappingMstrList.size());
			DocumentResponse docDetails = null;
			for(DocumentMappingMstr docMstr : documentMappingMstrList) {
				docDetails = new DocumentResponse();
				docDetails.setApplicationId(docMstr.getApplicationId());
				docDetails.setFilePath(docMstr.getFilePath());
				docDetails.setDocumentMappingId(docMstr.getId());
				docDetails.setDocumentMstrName(DocumentType.getDocumentType(docMstr.getDocumentMstrId()).getName());
				docDetails.setDocumentMstrId(docMstr.getDocumentMstrId());
				docDetails.setOriginalName(!CommonUtils.isObjectNullOrEmpty(docMstr.getOriginalName()) ? docMstr.getOriginalName() : "NA");
				docummentResponseList.add(docDetails);
			}
			documentResponse.setDocumentResponseList(docummentResponseList);
			docResponseList.add(documentResponse);
		}
		return docResponseList;
	}
	
	@Override
	public boolean inactiveDocument(Long id) {
		int inActiveDocument = documentMappingMstrRepository.inActiveDocument(id);
		return inActiveDocument > 0;
	}
	
	public Long updateUserDocMappingTable(Long documentId, Long userId, String filePath, String originalName) {
		Integer inactiveDoc = documentUserMappingMstrRepository.inactiveByUserId(userId, documentId);
		logger.info("TOTAL INACTVE DOCUMENT IS ----->" + inactiveDoc);
		DocumentUserMappingMstr documentMappingMstr = new DocumentUserMappingMstr();
		documentMappingMstr.setDocumentMstrId(documentId);
		documentMappingMstr.setUserId(userId);
		documentMappingMstr.setFilePath(filePath);
		documentMappingMstr.setCreatedBy(userId);
		documentMappingMstr.setCreatedDate(new Date());
		documentMappingMstr.setIsActive(true);
		documentMappingMstr.setOriginalName(originalName);
		documentMappingMstr = documentUserMappingMstrRepository.save(documentMappingMstr);
		return documentMappingMstr.getId();
	}
	
	public Long updateDocMappingTable(Long documentId, Long applicationId,Long userId, String filePath, String originalName) {
		Integer lastVersion = documentMappingMstrRepository.getLastVersion(documentId, applicationId);
		if(CommonUtils.isObjectNullOrEmpty(lastVersion)) {
			lastVersion = 1;
		}
		DocumentMappingMstr documentMappingMstr = new DocumentMappingMstr();
		documentMappingMstr.setApplicationId(applicationId);
		documentMappingMstr.setDocumentMstrId(documentId);
		documentMappingMstr.setUserId(userId);
		documentMappingMstr.setFilePath(filePath);
		documentMappingMstr.setCreatedBy(userId);
		documentMappingMstr.setCreatedDate(new Date());
		documentMappingMstr.setVersion(lastVersion + 1);
		documentMappingMstr.setIsActive(true);
		documentMappingMstr.setOriginalName(originalName);
		documentMappingMstr = documentMappingMstrRepository.save(documentMappingMstr);
		return documentMappingMstr.getId();
	}
	
	public String encryptFileName(String fileName) {
        logger.info("Entering in encryptFileName() {} ",fileName);
        Random r = new Random();
        String file[] = fileName.split("\\.");
        byte[] unencodedFile = file[0].getBytes();
        MessageDigest md = null;
        try {
            md = MessageDigest.getInstance("MD5");
        } catch (Exception e) {
            logger.error("file name not encrypted {}", e);
            e.printStackTrace();
        }
        md.reset();
        md.update(unencodedFile);
        byte[] encodedFile = md.digest();
        StringBuffer buf = new StringBuffer();
        for (int i = 0; i < encodedFile.length; i++) {
            if (((int) encodedFile[i] & 0xff) < 0x10) {
                buf.append("0");
            }
            buf.append(Long.toString((int) encodedFile[i] & 0xff, 16));
        }
        String encryptedFileName = (buf.toString()).concat(String.valueOf(r.nextInt()));
        logger.info("Exit with encryptFileName() {} ",encryptedFileName + "." + file[1]);
        return encryptedFileName + "." + file[1];
    }
	
}
