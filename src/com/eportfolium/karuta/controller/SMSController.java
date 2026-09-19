package com.eportfolium.karuta.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.eportfolium.karuta.service.SMSService;
import com.eportfolium.karuta.spring.AuthenticationService;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/sms")
public class SMSController {

	private final AuthenticationService authenticationService;
	private final SMSService smsService;

	public SMSController(AuthenticationService authenticationService, SMSService smsService) {
		this.authenticationService = authenticationService;
		this.smsService = smsService;
	}

	@PostMapping
	public ResponseEntity<?> getSMS(HttpServletRequest request) {
		final var userInfo = authenticationService.getAuthenticateUser(request);

		if (userInfo.isEmpty()) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}

		final var number = request.getParameter("number");

		final var intval = smsService.registerNumber(number);
		return ResponseEntity.ok(intval);
	}
}
