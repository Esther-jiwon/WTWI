package com.wtwi.fin.member.controller;

import java.security.PrivateKey;
import java.util.Locale;

import javax.inject.Inject;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.collections.bag.SynchronizedSortedBag;
import org.apache.maven.shared.utils.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.social.google.connect.GoogleConnectionFactory;
import org.springframework.social.oauth2.GrantType;
import org.springframework.social.oauth2.OAuth2Operations;
import org.springframework.social.oauth2.OAuth2Parameters;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.SessionAttributes;
import org.springframework.web.bind.support.SessionStatus;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.wtwi.fin.auth.SNSLogin;
import com.wtwi.fin.auth.SNSValue;
import com.wtwi.fin.member.model.service.MemberService;
import com.wtwi.fin.member.model.service.MemberServiceImpl;
import com.wtwi.fin.member.model.vo.Member;
import com.wtwi.fin.member.model.vo.RSA;
import com.wtwi.fin.member.model.vo.RSAUtil;


@Controller
@RequestMapping(value = "/member/*", method = RequestMethod.GET)
@SessionAttributes({ "loginMember" })
public class MemberController {

	@Autowired
	private MemberService service;
 
	@Autowired
	private RSAUtil rsaUtil;
  
	@Autowired
	@Qualifier("naverSns")
	private SNSValue naverSns;
	
	@Autowired
	@Qualifier("googleSns")
	private SNSValue googleSns;
	
	@Autowired
	@Qualifier("facebookSns")
	private SNSValue facebookSns;


	@Inject
	private GoogleConnectionFactory googleConnectionFactory;
	@Inject
	private OAuth2Parameters googleOAuth2Parameters;
	
	
	@RequestMapping(value="auth/{snsService}/callback", method=RequestMethod.GET) 
	public String snsLoginCallback(@PathVariable("snsService") String snsService, Model model, 
								   @RequestParam(value="code", required=false, defaultValue= "0") String code/*access Token 발급을 위한 code가 들어옴*/, 
								   RedirectAttributes ra,
								   HttpSession session) throws Exception{
		
		SNSValue sns = null;
		Member member = null;
		Member snsMember = null;
		String redirect = "";
		
		// 0) 페이스북 - 필수 동의 항목(이메일, 별명)에 동의하지 않았을 때 로그인 화면으로 돌리기
		if(code.equals("0")) {
			swalSetMessage(ra, "error", "로그인 실패", "필수 동의항목에 체크해주세요.");
			return "redirect:/member/login";
		}
		
		// 1) code를 이용해 accessToken 발급 -> 사용자 계정 정보 가져오기
		// 1-1) 카카오
		if(StringUtils.equals("kakao", snsService)) {
			SNSLogin snsLogin = new SNSLogin();
			snsMember = snsLogin.getKakaoProfile(code);		
		// 1-1) 네이버, 구글, 페이스북	
		}else {
			
			if(StringUtils.equals("naver", snsService)) {
				sns = naverSns;
			}else if(StringUtils.equals("google", snsService)) {
				sns = googleSns;
			} else if(StringUtils.equals("facebook", snsService)) {
				sns = facebookSns;
			}
			
			SNSLogin snsLogin = new SNSLogin(sns);
			snsMember = snsLogin.getUserProfile(code);
			
			// 1-2) 필수 동의 항목(이메일, 별명)에 동의하지 않았을 때 로그인으로 돌리고 강제 로그아웃
			if(snsMember.getMemberEmail() == null) {
				snsLogin.socialLogout(snsMember);
				swalSetMessage(ra, "error", "로그인 실패", "필수 동의항목에 체크해주세요.");
				return "redirect:/member/login";
			}
			
		}
		
		// 1-3) 필수 동의 항목(이메일, 별명)에 동의했을 때 해당 사용자 DB확인
		member = service.getSnsEmail(snsMember);
		
		// 1-4) 회원이 아니라면 회원가입 실행
		if(member == null) { 
			member = service.snsSignup(snsMember);
			if(member != null) {
				member.setAccessToken(snsMember.getAccessToken());
				swalSetMessage(ra, "success", "환영합니다 :)", null);
				model.addAttribute("loginMember", member);
			} else { 
				swalSetMessage(ra, "error", "회원가입 실패", "문제가 지속될 경우, 대표전화로 문의해주세요.");	
			}
			redirect = "/main";
			
		// 1-5) 회원이라면 로그인 실행
		} else { 
			member.setAccessToken(snsMember.getAccessToken());
			model.addAttribute("loginMember", member);	
			swalSetMessage(ra, "success", "로그인 성공!", null);
			
			// 1-6) 권한 처리 확인 후 이전페이지로 이동
			String dest = (String)session.getAttribute("dest");
			redirect = (dest == null) ? "/main" : dest;
			
			session.removeAttribute("dest");

		}
		
		return "redirect:" + redirect;
	}

	@RequestMapping("signUp")
	public String signUp() {

		return "member/signUp";

	}

	@RequestMapping(value = "signUpAction", method = RequestMethod.POST)
	public String signUp(@ModelAttribute Member inputMember, RedirectAttributes ra) {

		int result = service.signUp(inputMember);
		if (result > 0) {
			swalSetMessage(ra, "success", "회원가입 성공!", "로그인 후 사이트를 이용해주세요 :)");
		} else {
			swalSetMessage(ra, "error", "회원가입 실패", "문제가 지속될 경우, 대표전화로 문의해주세요.");
		}
		return "redirect:/main";

	}

	@ResponseBody
	@RequestMapping(value = "idDupCheck", method = RequestMethod.POST)
	public int idDupCheck(@RequestParam("id") String id, RedirectAttributes ra) {

		int result = service.idDupCheck(id);

		return result;
	}

	// 로그인 페이지 진입
	@RequestMapping(value = "login", method = RequestMethod.GET)
	public String loginForm(HttpSession session, Model model) {
		
		///////////////////////////// RSA 개인키 발급 //////////////////////////////////
		PrivateKey key = (PrivateKey) session.getAttribute("RSAprivateKey");
		if (key != null) { 
			session.removeAttribute("RSAprivateKey");
		}
		RSA rsa = rsaUtil.createRSA();
		model.addAttribute("modulus", rsa.getModulus());
		model.addAttribute("exponent", rsa.getExponent());
		session.setAttribute("RSAprivateKey", rsa.getPrivateKey());

		///////////////////////////// 소셜 로그인 //////////////////////////////////
		// 1) Naver
		SNSLogin naverLogin = new SNSLogin(naverSns);
		model.addAttribute("naver_url", naverLogin.getSNSAuthURL());
		
		// 2) Kakao는 RestTemplate을 이용한 방식 
		model.addAttribute("kakao_url", "https://kauth.kakao.com/oauth/authorize?response_type=code&client_id=b87de33b6c8fe6b2977868b55731dae3&redirect_uri=http%3A%2F%2Flocalhost%3A8080%2Fwtwi%2Fmember%2Fauth%2Fkakao%2Fcallback");

		// 2-1) Facebook
		SNSLogin facebookLogin = new SNSLogin(facebookSns);
		model.addAttribute("facebook_url", facebookLogin.getSNSAuthURL());

		// 3) Google
		OAuth2Operations oauthOperations = googleConnectionFactory.getOAuthOperations();
		String url = oauthOperations.buildAuthorizeUrl(GrantType.AUTHORIZATION_CODE, googleOAuth2Parameters);
		model.addAttribute("google_url", url);

		return "member/login";
	}

	@RequestMapping(value = "loginAction", method = RequestMethod.POST)
	public String login(@RequestParam(value="save", required=false) String save ,
						Member member, HttpSession session, RedirectAttributes ra, Model model,
						HttpServletRequest request, HttpServletResponse response) {

		PrivateKey key = (PrivateKey) session.getAttribute("RSAprivateKey");

		if (key == null) {
			swalSetMessage(ra, "error", "로그인 실패", "문제가 지속될 경우, 대표번호로 문의바랍니다.");
			return "redirect:/member/login";
		}

		session.removeAttribute("RSAprivateKey");

		try {
			String memberId = rsaUtil.getDecryptText(key, member.getMemberId());
			String memberPw = rsaUtil.getDecryptText(key, member.getMemberPw());

			member.setMemberId(memberId);
			member.setMemberPw(memberPw);
		} catch (Exception e) {
			e.printStackTrace();
			swalSetMessage(ra, "error", "로그인 실패", "문제가 지속될 경우, 대표번호로 문의바랍니다.");
			return "redirect:/member/login";
		}
		
		// 로그인 로직 실행
		Member loginMember = service.login(member);

		if (loginMember != null) {
			model.addAttribute("loginMember", loginMember);
			Cookie cookie = new Cookie("saveId", loginMember.getMemberId());
			
			swalSetMessage(ra, "success", "로그인 성공!", null);
			
			if (save != null) {
				cookie.setMaxAge(60 * 60 * 24 * 30);
			} else {
				cookie.setMaxAge(0);
			}

			cookie.setPath(request.getContextPath());
			response.addCookie(cookie);

		} else {
			swalSetMessage(ra, "error", "로그인 실패", "아이디 또는 비밀번호가 일치하지 않습니다.");
			return "redirect:/member/login";
		}
		String dest = (String)session.getAttribute("dest");
		String redirect = (dest == null) ? "/main" : dest;
		session.removeAttribute("dest");
		return "redirect:" + redirect;

	}

	@RequestMapping(value = "logout", method = RequestMethod.GET)
	public String logout(SessionStatus status, @ModelAttribute("loginMember") Member loginMember, RedirectAttributes ra) {
		
		SNSValue sns = null;
		
		switch(loginMember.getMemberGrade()) {
		case "N" :
		case "G" :
		case "F" :
		case "K" : 
			try {
				if(loginMember.getMemberGrade().equals("N")){
					// 네이버는 로그아웃 주소에 clientId, clientSecret이 들어가기 때문에 naver 정보를 넣어서 넘겨줌
					sns = naverSns;
					SNSLogin snsLogin = new SNSLogin(sns);
					snsLogin.socialLogout(loginMember);
					break;
				} else {
					SNSLogin snsLogin = new SNSLogin();
					snsLogin.socialLogout(loginMember);
					break;
				} 
			}catch(Exception e) {
				e.printStackTrace();
				break;
			} 
		}
		
		status.setComplete();
		swalSetMessage(ra, "success", "로그아웃 완료", null);

		return "redirect:/main";
	}
	
	@RequestMapping(value="searchIdForm", method=RequestMethod.GET)
		public String searchIdForm() {
		return "member/searchId";
	}
	
	@RequestMapping(value="searchPwForm", method=RequestMethod.GET)
	public String searchPwForm() {
	return "member/searchPw";
	}

	@RequestMapping(value = "myPage", method = RequestMethod.GET)
	public String myPage() {
		return "myPage/main";
	}
	
	@RequestMapping(value="update", method=RequestMethod.GET)
	public String update() {
		return "member/update";
	}

	@RequestMapping(value = "updateAction", method = RequestMethod.POST)
	public String updateAction(@ModelAttribute("loginMember") Member loginMember, Member inputMember, String inputEmail, String inputNickname, 
			String inputPhone, RedirectAttributes ra) {

		inputMember.setMemberNo(loginMember.getMemberNo());
		inputMember.setMemberEmail(inputEmail);
		inputMember.setMemberPhone(inputPhone);
		inputMember.setMemberNick(inputNickname);

		int result = service.updateMember(inputMember);

		if (result > 0) {
			swalSetMessage(ra, "success", "내 정보 수정 성공!", null);
			if(inputEmail != null) {				
				loginMember.setMemberEmail(inputEmail);
				loginMember.setMemberPhone(inputPhone);
			}
			loginMember.setMemberNick(inputNickname);
		} else {
			swalSetMessage(ra, "error", "내 정보 수정 실패", null);
		}
		return "redirect:/member/update";
	}

	public static void swalSetMessage(RedirectAttributes ra, String icon, String title, String text) {
		ra.addFlashAttribute("icon", icon);
		ra.addFlashAttribute("title", title);
		ra.addFlashAttribute("text", text);
	}



	@RequestMapping(value = "changePwd", method = RequestMethod.GET)
	public String changPwd() {
		return "member/changePwd";
	}

	@RequestMapping(value = "changePwdAction", method = RequestMethod.POST)
	public String changePwd(@ModelAttribute("loginMember") Member loginMember, String currentPwd,
			@RequestParam("newPwd1") String newPwd, RedirectAttributes ra) {

		int result = service.changePwd(currentPwd, newPwd, loginMember);
		if(result > 0) {
			swalSetMessage(ra, "success", "비밀번호 수정 성공", null);
		} else {
			swalSetMessage(ra, "error", "비밀번호 수정 실패", null);
		}
		
		return "redirect:changePwd";
	}

	@RequestMapping(value = "secession", method = RequestMethod.GET)
	public String secession() {
		return "member/secession";
	}

	@RequestMapping(value = "secessionAction", method = RequestMethod.POST)
	public String secession(@ModelAttribute("loginMember") Member loginMember,
			@RequestParam(value="currentPwd", required = false) String currentPwd, RedirectAttributes ra, SessionStatus status) {
		
		int result = service.secession(currentPwd, loginMember);
		String path = "redirect:";
		if (result > 0) {
			swalSetMessage(ra, "success", "회원탈퇴가 완료되었습니다.", null);
			status.setComplete();
			path += "/";
			
		} else {
			swalSetMessage(ra, "error", "회원탈퇴를 실패하였습니다.", null);
			path += "secession";
		}
		
		
		return path;
	}
	
	@ResponseBody
	@RequestMapping(value="searchId", method=RequestMethod.POST)
	public String searchId(String memberEmail) {
		Member member = service.searchId(memberEmail);

		String memberId = null;
		if(member != null) {
			memberId = member.getMemberId();
		} 
		return memberId;
	}
	
	
	

		
	

}