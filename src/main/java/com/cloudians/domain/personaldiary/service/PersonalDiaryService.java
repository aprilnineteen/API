package com.cloudians.domain.personaldiary.service;

import com.cloudians.domain.personaldiary.dto.request.PersonalDiaryCreateRequest;
import com.cloudians.domain.personaldiary.dto.request.PersonalDiaryEmotionCreateRequest;
import com.cloudians.domain.personaldiary.dto.request.PersonalDiaryEmotionUpdateRequest;
import com.cloudians.domain.personaldiary.dto.request.PersonalDiaryUpdateRequest;
import com.cloudians.domain.personaldiary.dto.response.*;
import com.cloudians.domain.personaldiary.entity.PersonalDiary;
import com.cloudians.domain.personaldiary.entity.PersonalDiaryEmotion;
import com.cloudians.domain.personaldiary.entity.analysis.*;
import com.cloudians.domain.personaldiary.exception.PersonalDiaryException;
import com.cloudians.domain.personaldiary.exception.PersonalDiaryExceptionType;
import com.cloudians.domain.personaldiary.repository.*;
import com.cloudians.domain.user.entity.User;
import com.cloudians.domain.user.exception.UserException;
import com.cloudians.domain.user.exception.UserExceptionType;
import com.cloudians.domain.user.repository.UserRepository;
import com.cloudians.global.service.FirebaseService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.auth.FirebaseAuthException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class PersonalDiaryService {

    private final ChatGptService chatGptService;
    private final FirebaseService firebaseService;

    private final UserRepository userRepository;
    private final PersonalDiaryRepository personalDiaryRepository;
    private final PersonalDiaryEmotionRepository personalDiaryEmotionRepository;
    private final PersonalDiaryAnalysisRepository personalDiaryAnalysisRepository;
    private final FiveElementCharacterRepository fiveElementCharacterRepository;
    private final FiveElementRepository fiveElementRepository;
    private final HarmonyTipRepository harmonyTipRepository;

    private Map<String, PersonalDiaryEmotion> tempEmotions = new HashMap<>();
    public static String DOMAIN = "diary";

    public PersonalDiaryEmotionCreateResponse createTempSelfEmotions(PersonalDiaryEmotionCreateRequest request, String userEmail) {
        User user = findUserByUserEmail(userEmail);
        //감정 수치 검증
        validateEmotionsValue(request);
        PersonalDiaryEmotion personalDiaryEmotion = request.toEntity(user);
        //감정들 임시저장소에 저장
        tempEmotions.put(user.getUserEmail(), personalDiaryEmotion);

        return PersonalDiaryEmotionCreateResponse.of(personalDiaryEmotion, user);
    }


    public PersonalDiaryEmotionUpdateResponse editSelfEmotions(PersonalDiaryEmotionUpdateRequest request, Long emotionId, String userEmail) {
        User user = findUserByUserEmail(userEmail);
        // 수정할 감정이 있는지 확인
        PersonalDiaryEmotion emotions = personalDiaryEmotionRepository.findByIdAndUser(emotionId, user)
                .orElseThrow(() -> new PersonalDiaryException(PersonalDiaryExceptionType.NON_EXIST_PERSONAL_DIARY));
        //수정
        PersonalDiaryEmotion editedEmotions = emotions.edit(request);

        return PersonalDiaryEmotionUpdateResponse.of(editedEmotions);
    }

    public PersonalDiaryCreateResponse createPersonalDiary(PersonalDiaryCreateRequest request, String userEmail, MultipartFile file) throws Exception {
        //TODO: throw Exception 지우기
        User user = findUserByUserEmail(userEmail);
        //감정 수치 가져오기
        PersonalDiaryEmotion emotions = getTempEmotion(user.getUserEmail());
        //없으면 예외처리
        validateEmotionsExistenceAndThrow(emotions);
        //이미 해당날짜에 일기 썼는지 확인
        validateDuplicateDateDiaryAndThrow(user, emotions);

        personalDiaryEmotionRepository.save(emotions);
        //감정들 임시저장소에서 삭제
        removeTempEmotion(user.getUserEmail());

        String photoUrl = getPhotoUrl(userEmail, file);
        PersonalDiary personalDiary = request.toEntity(user, emotions, photoUrl);
        PersonalDiary savedPersonalDiary = personalDiaryRepository.save(personalDiary);

        return PersonalDiaryCreateResponse.of(savedPersonalDiary, user, emotions);
    }

    private String getPhotoUrl(String userEmail, MultipartFile file) throws IOException, FirebaseAuthException {
        return file != null
                //TODO: fileName설정
                ? firebaseService.uploadFile(file, userEmail, file.getOriginalFilename(), DOMAIN)
                : null;
    }

    public PersonalDiaryResponse getPersonalDiary(String userEmail, Long personalDiaryId) {
        User user = findUserByUserEmail(userEmail);

        PersonalDiary personalDiary = getPersonalDiaryOrThrow(personalDiaryId, user);
        return PersonalDiaryResponse.of(personalDiary);
    }

    public PersonalDiaryResponse editPersonalDiary(PersonalDiaryUpdateRequest request, Long personalDiaryId, String userEmail, MultipartFile file) throws Exception {
        User user = findUserByUserEmail(userEmail);
        // 수정할 일기가 있는지 확인
        PersonalDiary personalDiary = getPersonalDiaryOrThrow(personalDiaryId, user);
        String photoUrl = getPhotoUrl(userEmail, file);
        if (photoUrl != null) {
//            firebaseService.deleteFileUrl(personalDiary.getPhotoUrl());
        }
        PersonalDiary editedPersonalDiary = personalDiary.edit(request, photoUrl);

        return PersonalDiaryResponse.of(editedPersonalDiary);
    }

    public void deletePersonalDiary(String userEmail, Long personalDiaryId) {
        User user = findUserByUserEmail(userEmail);

        PersonalDiary personalDiary = getPersonalDiaryOrThrow(personalDiaryId, user);
//        firebaseService.deleteFileUrl("users/dencoding@naver.com/diary/Screenshot 2024-08-09 at 15.48.00.png");
        personalDiaryRepository.delete(personalDiary);
        personalDiaryEmotionRepository.delete(personalDiary.getEmotion());
    }

    public PersonalDiaryAnalyzeResponse analyzePersonalDiary(String userEmail, Long personalDiaryId) throws Exception {
        User user = findUserByUserEmail(userEmail);
        PersonalDiary personalDiary = getPersonalDiaryOrThrow(personalDiaryId, user);

        String[] analysisResults = analyzeDiaryWithChatGPT(personalDiary, user);
        FiveElement element = fiveElementRepository.findByName(analysisResults[0]);
        List<String> characters = getElementCharacters(element);

        String harmonyTipsJson = getHarmonyTipsJson();
        PersonalDiaryAnalysis personalDiaryAnalysis = buildPersonalDiaryAnalysis(user, personalDiary, element, characters, harmonyTipsJson, analysisResults);

        personalDiaryAnalysisRepository.save(personalDiaryAnalysis);

        return PersonalDiaryAnalyzeResponse.of(personalDiaryAnalysis, user);
    }

    private String[] analyzeDiaryWithChatGPT(PersonalDiary personalDiary, User user) throws Exception {
        String answer = chatGptService.askQuestion(personalDiary, user);
        System.out.println("answer = " + answer);
        return new String[]{
                answer.split("\n")[0].split(": ")[1],  // elementName
                answer.split("\n")[1].split(": ")[1],  // fortuneDetail
                answer.split("\n")[2].split(": ")[1]   // advice
        };
    }

    private List<String> getElementCharacters(FiveElement element) {
        return fiveElementCharacterRepository.findRandomCharactersByElementId(element.getId())
                .stream()
                .map(FiveElementCharacter::getCharacter)
                .collect(Collectors.toList());
    }

    private String getHarmonyTipsJson() throws Exception {
        List<HarmonyTip> harmonyTips = harmonyTipRepository.findRandomTipsByTags("#활동적", "#차분한", "#창의적");
            List<HarmonyTipsResponse> harmonyTipsResponse = harmonyTips.stream()
                .map(HarmonyTipsResponse::of)
                .collect(Collectors.toList());
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writeValueAsString(harmonyTipsResponse);
    }

    private PersonalDiaryAnalysis buildPersonalDiaryAnalysis(User user, PersonalDiary personalDiary, FiveElement element, List<String> characters, String harmonyTipsJson, String[] analysisResults) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        String elementCharactersJson = objectMapper.writeValueAsString(characters);

        return PersonalDiaryAnalysis.builder()
                .user(user)
                .personalDiary(personalDiary)
                .fiveElement(element)
                .elementCharacters(elementCharactersJson)
                .harmonyTips(harmonyTipsJson)
                .fortuneDetail(analysisResults[1])
                .advice(analysisResults[2])
                .build();
    }

    private PersonalDiary getPersonalDiaryOrThrow(Long personalDiaryId, User user) {
        return personalDiaryRepository.findByIdAndUser(personalDiaryId, user)
                .orElseThrow(() -> new PersonalDiaryException(PersonalDiaryExceptionType.NON_EXIST_PERSONAL_DIARY));
    }

    private void validateEmotionsValue(PersonalDiaryEmotionCreateRequest request) {
        List<Integer> emotions = Arrays.asList(
                request.getJoy(),
                request.getSadness(),
                request.getAnger(),
                request.getAnxiety(),
                request.getBoredom()
        );
        emotions.forEach(this::validateEmotionValue);
    }

    private void validateEmotionValue(int emotion) {
        if (emotion % 10 != 0) {
            throw new PersonalDiaryException(PersonalDiaryExceptionType.EMOTION_VALUE_WRONG_INPUT);
        }
    }

    private PersonalDiaryEmotion getTempEmotion(String userEmail) {
        return tempEmotions.get(userEmail);
    }

    private void removeTempEmotion(String userEmail) {
        tempEmotions.remove(userEmail);
    }


    private void validateEmotionsExistenceAndThrow(PersonalDiaryEmotion emotions) {
        if (emotions == null) {
            throw new PersonalDiaryException(PersonalDiaryExceptionType.NO_EMOTION_DATA);
        }
    }

    private void validateDuplicateDateDiaryAndThrow(User user, PersonalDiaryEmotion emotions) {
        boolean isExist = personalDiaryRepository.existsPersonalDiaryByUserAndDate(user, emotions.getDate());
        if (isExist) {
            throw new PersonalDiaryException(PersonalDiaryExceptionType.PERSONAL_DIARY_ALREADY_EXIST);
        }
    }

    private User findUserByUserEmail(String userEmail) {
        return userRepository.findByUserEmail(userEmail)
                .orElseThrow(() -> new UserException(UserExceptionType.USER_NOT_FOUND));
    }
}