package com.wtwi.fin.auth;

public interface SnsUrls {
	
	static final String NAVER_AUTH = "https://nid.naver.com/oauth2.0/authorize";
	static final String NAVER_ACCESS_TOKEN = "https://nid.naver.com/oauth2.0/token";
	static final String NAVER_PROFILE_URL = "https://openapi.naver.com/v1/nid/me";
	static final String NAVER_LOGOUT_URL = "https://nid.naver.com/oauth2.0/token?grant_type=delete&service_provider=NAVER";

	
	static final String FACEBOOK_ACCESS_TOKEN = "https://graph.facebook.com/v11.0/oauth/access_token";
	static final String FACEBOOK_AUTH = "https://www.facebook.com/v11.0/dialog/oauth";
	static final String FACEBOOK_PROFILE_URL = "https://graph.facebook.com/me?fields=email,name";
	

	static final String KAKAO_ACCESS_TOKEN = "https://kauth.kakao.com/oauth/token";
	static final String KAKAO_AUTH = "https://kauth.kakao.com/oauth/authorize";
	static final String KAKAO_PROFILE_URL = "https://kapi.kakao.com/v2/user/me";	
	static final String KAKAO_LOGIN_URL = "https://kauth.kakao.com/oauth/authorize?response_type=code&client_id=아이디&redirect_uri=http%3A%2F%2Flocalhost%3A8080%2Ffin%2Fmember%2Fauth%2Fkakao%2Fcallback";
	
	static final String GOOGLE_PROFILE_URL = "https://www.googleapis.com/oauth2/v3/userinfo";
	static final String GOOGLE_LOGOUT_URL = "https://accounts.google.com/logout";
	
	
}
  
