package com.cloudians.domain.user.service;

import java.util.Optional;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.cloudians.domain.user.dto.request.UserRequest;
import com.cloudians.domain.user.dto.response.UserResponse;
import com.cloudians.domain.user.entity.User;
import com.cloudians.domain.user.exception.UserException;
import com.cloudians.domain.user.exception.UserExceptionType;
import com.cloudians.domain.user.repository.UserRepository;
import com.cloudians.global.service.FirebaseService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService{
	
	private final FirebaseService firebaseService;
	@Autowired
	private BCryptPasswordEncoder bCryptPasswordEncoder = new BCryptPasswordEncoder();
	private final UserRepository userRepository;


    
    //signup_type
    public UserResponse userSignup(UserRequest userRequest) {
	Optional<User> user = userRepository.findByUserEmail(userRequest.getUserEmail());
	User join = new User();
	if(user.isEmpty()) {
	    join.setUserEmail(userRequest.getUserEmail());
	    join.setNickname(userRequest.getNickname());
	    join.setName(userRequest.getName());
	    join.setPassword(bCryptPasswordEncoder.encode(userRequest.getPassword()));
	    join.setGender(userRequest.getGender());
	    join.setCalendarType(userRequest.getCalendarType());
	    join.setBirthdate(userRequest.getBirthdate());
	    join.setBirthTime(userRequest.getBirthTime());
	    User NewUser = userRepository.save(join);
	    return NewUser.toDto();
	} else return null;
	
	
    }
	
	public UserResponse findByEmail(String userEmail) throws NullPointerException {
	    System.out.println("findByEmailService start:"+userEmail);
		Optional<User> user = userRepository.findById(userEmail);
		System.out.println(user.get().getUserEmail());
		if(!user.isPresent()) {throw new UserException(UserExceptionType.USER_NOT_FOUND);}
		UserResponse userResponse = user.get().toDto();
		return userResponse;
	}


	public UserResponse updateUser(String userEmail, UserRequest userRequest) {
		System.out.println("조회부터 할게염.");
		Optional<User> optionalUser = userRepository.findById(userEmail);
		System.out.println(optionalUser.toString()+"조회 완료.");
		if (optionalUser.isEmpty()) {
	        return null;
	    }
		
		User user = optionalUser.get();
		System.out.println(user.getUserEmail()+"변환 완뇨.");
	    if (userRequest.getName() != null) {
	        user.setName(userRequest.getName());
	    }
	    if (userRequest.getNickname() != null) {
	        user.setNickname(userRequest.getNickname());
	    }
	   if(userRequest.getBirthdate()!=null) {
		   user.setBirthdate(userRequest.getBirthdate());
	   }
	   if(userRequest.getBirthTime()!=null) {
		   user.setBirthTime(userRequest.getBirthTime());
	   }
	   if(userRequest.getCalendarType()!=null) {
		   user.setCalendarType(userRequest.getCalendarType());
	   }
	    
	    User updatedUser = userRepository.save(user);
	    System.out.println(updatedUser.getName());
	    return updatedUser.toDto();
	}
	
	public UserResponse updateProfile(String userEmail, MultipartFile file) throws Exception{
		Optional<User> optionalUser = userRepository.findById(userEmail);
		System.out.println(optionalUser.toString()+"조회 완료.");
		if (optionalUser.isEmpty()) {
	        return null;
	    }
		User user = optionalUser.get();
		    String domain = "profile";
			firebaseService.uploadFile(file, user.getNickname().toString(),"profile",domain);
			String url = firebaseService.uploadFile(file, user.getNickname().toString(),"profile",domain);
			   user.setProfileUrl(url);
			   User updatedUser = userRepository.save(user);
			   return updatedUser.toDto();

	}
	
	
	public UserResponse deleteUserProfile(String userEmail) throws Exception{
		Optional<User> optionalUser = userRepository.findById(userEmail);
		System.out.println(optionalUser.toString()+"조회 완료.");
		if (optionalUser.isEmpty()) {
	        return null;
	    }
		User user = optionalUser.get();
		firebaseService.deleteFileUrl(userEmail,"profile","123");
		user.setProfileUrl(null);
		  User updatedUser = userRepository.save(user);		  
		  return updatedUser.toDto();
	}

}