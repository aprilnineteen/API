package com.cloudians.global.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.cloudians.domain.user.entity.User;
import com.cloudians.domain.user.exception.UserException;
import com.cloudians.domain.user.exception.UserExceptionType;
import com.cloudians.domain.user.repository.UserRepository;
import com.cloudians.domain.user.service.UserService;
import com.cloudians.global.exception.FirebaseException;
import com.cloudians.global.exception.FirebaseExceptionType;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import com.google.cloud.storage.StorageOptions;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.cloud.StorageClient;

@Service
public class FirebaseService {
	
	private final Storage storage;
	private final String firebaseBucket = "cloudians-photo.appspot.com";
	private UserService userService;
	private UserRepository userRepository;
	private FirebaseAuth fireAuth;
	

	    @Autowired
	    public FirebaseService() {
	        // Ensure FirebaseApp is initialized before accessing services
	        if (FirebaseApp.getApps().isEmpty()) {
	            throw new IllegalStateException("FirebaseApp is not initialized.");
	        }
	        this.storage = StorageOptions.getDefaultInstance().getService();
	        this.userRepository=userRepository;
	        this.fireAuth = FirebaseAuth.getInstance();
	    }
    
    public Bucket bucket() {
	return StorageClient.getInstance().bucket(firebaseBucket);
    }
    
    // file name unique
    public String generateUniqueFileName(String originalFileName) {
        String uniqueFileName = UUID.randomUUID().toString() + "_" + originalFileName;
        return uniqueFileName;
    }
 
    
    // user folder
    public String folderPath(String userEmail, String domain,String fileName) {
	 // 사용자 폴더 경로 설정
	System.out.println(userEmail);
	Optional<User> user =userRepository.findById(userEmail);
	if(user.isEmpty()) {
	    throw new UserException(UserExceptionType.USER_NOT_FOUND);
	} else {
	    String folderPath = "users/"+userEmail+"/"+domain+"/"+fileName.toString(); // 사용자 ID에 따라 폴더를 생성
	        return folderPath; 
	}
    }
    
   
	
	// upload file & fileName 파일 이름 unique
		public String uploadFile(MultipartFile file, String userEmail, String fileName, String domain) throws IOException, FirebaseAuthException {
		    try (InputStream content = new ByteArrayInputStream(file.getBytes())) {
		        Blob blob = bucket().create(folderPath(userEmail,domain,generateUniqueFileName(fileName)),content,file.getContentType());
		        return blob.getMediaLink();
		    } catch (StorageException e) {
		        System.err.println("StorageException: " + e.getMessage());
		        throw e;
		    } catch (IOException e) {
		        System.err.println("IOException: " + e.getMessage());
		        throw e;
		    }
		}
	
	
    
    // delete file
    public void deleteFileUrl(String userEmail, String domain, String fileName) {
	System.out.println(fileName);
	String folderPath = folderPath(userEmail,domain,fileName);
	Blob blob = bucket().get(folderPath);
	if(blob != null) {
		blob.delete();
	} else throw new FirebaseException(FirebaseExceptionType.PHOTO_VALUE_NOT_FOUND);
	
    }
    
	// get file url
    public String getFileUrl(String userEmail, String domain, String fileName) throws Exception {
	    String folderPath = folderPath(userEmail,domain,fileName);
        Blob blob = bucket().get(folderPath);
            if(blob != null) {
        	String urlPath = folderPath.split("/")[0]+"%2F"+folderPath.split("/")[1]+"%2F"+folderPath.split("/")[2]+"%2F"+folderPath.split("/")[3];
        	System.out.println(folderPath);
        	System.out.println("mediaLink:"+blob.getMediaLink());
        	System.out.println("selfLink:"+blob.getSelfLink());
        	System.out.println("blob:"+blob.toString());
        	 Map<String, String> metadata = blob.getMetadata();
        	 String tokens = metadata.get("firebaseStorageDownloadTokens");
        	 
        	String dap = "https://firebasestorage.googleapis.com/v0/b/cloudians-photo.appspot.com/o/"+urlPath+"?alt=media&token="+tokens;
        	return dap;
            }
            else throw new FirebaseException(FirebaseExceptionType.PHOTO_VALUE_NOT_FOUND);
    }
    


}
