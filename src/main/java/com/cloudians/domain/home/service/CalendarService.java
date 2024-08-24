package com.cloudians.domain.home.service;

import com.cloudians.domain.home.dto.response.CalendarResponse;
import com.cloudians.domain.home.dto.response.EmotionsResponse;
import com.cloudians.domain.home.entity.SenderType;
import com.cloudians.domain.home.exception.CalendarException;
import com.cloudians.domain.home.exception.CalendarExceptionType;
import com.cloudians.domain.home.repository.WhisperMessageRepositoryImpl;
import com.cloudians.domain.personaldiary.dto.response.PersonalDiaryResponse;
import com.cloudians.domain.personaldiary.entity.PersonalDiary;
import com.cloudians.domain.personaldiary.exception.PersonalDiaryException;
import com.cloudians.domain.personaldiary.exception.PersonalDiaryExceptionType;
import com.cloudians.domain.personaldiary.repository.PersonalDiaryRepository;
import com.cloudians.domain.user.entity.User;
import com.cloudians.domain.user.exception.UserException;
import com.cloudians.domain.user.exception.UserExceptionType;
import com.cloudians.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
@Transactional
public class CalendarService {
    private final PersonalDiaryRepository personalDiaryRepository;
    private final WhisperMessageRepositoryImpl whisperMessageRepository;

    private final UserRepository userRepository;

    public List<CalendarResponse> getDiaries(String userEmail) {
        User user = findUserByUserEmail(userEmail);

//        YearMonth yearMonth = YearMonth.from(date);
//        LocalDate startOfMonth = yearMonth.atDay(1);
//        LocalDate endOfMonth = yearMonth.atEndOfMonth();

        List<PersonalDiary> diaries = getPersonalDiariesOrThrow(user);
        Set<LocalDate> whisperMessageDates = getWhisperMessageDates(user);

        return getCalendarResponses(diaries, whisperMessageDates);
    }

    public PersonalDiaryResponse getPersonalDiary(String userEmail, LocalDate date) {
        User user = findUserByUserEmail(userEmail);
        PersonalDiary personalDiary = getPersonalDiaryOrThrow(user, date);

        return PersonalDiaryResponse.of(personalDiary);
    }

    private PersonalDiary getPersonalDiaryOrThrow(User user, LocalDate date) {
        return personalDiaryRepository.findByUserAndDate(user, date)
                .orElseThrow(() -> new PersonalDiaryException(PersonalDiaryExceptionType.NON_EXIST_PERSONAL_DIARY));
    }

    private List<CalendarResponse> getCalendarResponses(List<PersonalDiary> diaries, Set<LocalDate> whisperMessageDates) {
        return diaries.stream()
                .map(diary -> {
                    EmotionsResponse emotionsResponse = EmotionsResponse.of(diary.getEmotion());
                    boolean hasAnswer = whisperMessageDates.contains(diary.getDate());
                    return CalendarResponse.of(diary, emotionsResponse, hasAnswer);
                })
                .collect(Collectors.toList());
    }

    private Set<LocalDate> getWhisperMessageDates(User user) {
        return whisperMessageRepository.findByUserAndSender(
                        user, SenderType.USER)
                .stream()
                .map(message -> message.getTimestamp().toLocalDate())
                .collect(Collectors.toSet());
    }

    private List<PersonalDiary> getPersonalDiariesOrThrow(User user) {
        return personalDiaryRepository.findPersonalDiaryByUserOrderByDate(user)
                .orElseThrow(() -> new CalendarException(CalendarExceptionType.NO_MORE_DATA));
    }

    private User findUserByUserEmail(String userEmail) {
        return userRepository.findByUserEmail(userEmail)
                .orElseThrow(() -> new UserException(UserExceptionType.USER_NOT_FOUND));
    }
}

