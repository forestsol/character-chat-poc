package com.example.characterchat.profile.application;

public class ProfileNotFoundException extends RuntimeException {
	public ProfileNotFoundException(Long bookId) { super("캐릭터 프로필을 찾을 수 없습니다. bookId=" + bookId); }
}
