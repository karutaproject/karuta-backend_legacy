/* =======================================================
	Copyright 2026 - ePortfolium - Licensed under the
	Educational Community License, Version 2.0 (the "License"); you may
	not use this file except in compliance with the License. You may
	obtain a copy of the License at

	http://www.osedu.org/licenses/ECL-2.0

	Unless required by applicable law or agreed to in writing,
	software distributed under the License is distributed on an "AS IS"
	BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
	or implied. See the License for the specific language governing
	permissions and limitations under the License.
   ======================================================= */

package com.eportfolium.karuta.controller;

import com.eportfolium.karuta.entity.SMSRegistrationEntity.ConfirmationAction;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.eportfolium.karuta.service.SMSService;
import com.eportfolium.karuta.spring.AuthenticationService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.UUID;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@Validated
@RequestMapping("/sms")
public class SMSController {
    private static final String PHONE_PATTERN = "^\\+\\d+$";
    private static final String PHONE_PATTERN_ERROR = "Phone number must start with + and contain only digits";
    
	private final AuthenticationService authenticationService;
	private final SMSService smsService;

	public SMSController(AuthenticationService authenticationService, SMSService smsService) {
		this.authenticationService = authenticationService;
		this.smsService = smsService;
	}

	@PostMapping("/register")
	public ResponseEntity<?> registerNumber(HttpServletRequest request,
            @RequestParam("number")
            @NotBlank
            @Pattern(message = PHONE_PATTERN_ERROR,
                    regexp = PHONE_PATTERN) final String number) {
		final var userInfo = authenticationService.getAuthenticateUser(request);

		if (userInfo.isEmpty()) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}

		final var intval = smsService.registerNumber((long)userInfo.get().userId, number, ConfirmationAction.REGISTER);
		return ResponseEntity.ok(intval);
	}

	@PostMapping("/deregister")
	public ResponseEntity<?> deRegisterNumber(HttpServletRequest request,
            @RequestParam("number")
            @NotBlank
            @Pattern(message = PHONE_PATTERN_ERROR,
                    regexp = PHONE_PATTERN) final String number) {
		final var userInfo = authenticationService.getAuthenticateUser(request);

		if (userInfo.isEmpty()) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}

		final var intval = smsService.registerNumber((long)userInfo.get().userId, number, ConfirmationAction.DEREGISTER);
		return ResponseEntity.ok(intval);
	}

    @PostMapping("/confirm")
	public ResponseEntity<?> confirmRegister(HttpServletRequest request, @RequestParam("number") final String number) {
		final var userInfo = authenticationService.getAuthenticateUser(request);

		if (userInfo.isEmpty()) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}

		final var intval = smsService.validateNumber((long)userInfo.get().userId, number);
		return ResponseEntity.ok(intval);
	}
    
	@PostMapping("/webhook")
	public ResponseEntity<?> webhook(@RequestBody WebhookRequest request) {
        String eventType = request.data().eventType();
        String eventId = request.data().id();
        String messageId = request.data().payload().id();
        String phoneNumber = request.data().payload().to().get(0).phoneNumber();
        String status = request.data().payload().to().get(0).status();
        Instant occuredAt = request.data().occurredAt();


		final var intval = smsService.handleWebhook(eventType,
        occuredAt, status,
        UUID.fromString(eventId),
        UUID.fromString(messageId),
        phoneNumber);
		return ResponseEntity.ok().build();
	}
}
