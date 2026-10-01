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

package com.eportfolium.karuta.service;

import com.eportfolium.karuta.entity.SMSEntity;
import com.eportfolium.karuta.entity.SMSRegistrationEntity;
import com.eportfolium.karuta.repository.SMSRegistrationRepository;
import org.springframework.stereotype.Service;

import com.eportfolium.karuta.repository.SMSRepository;
import com.telnyx.sdk.client.TelnyxClient;
import com.telnyx.sdk.client.okhttp.TelnyxOkHttpClient;
import com.telnyx.sdk.errors.RateLimitException;
import com.telnyx.sdk.errors.TelnyxException;
import com.telnyx.sdk.models.messages.MessageSendParams;
import com.telnyx.sdk.models.messages.MessageSendResponse;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Service
public class SMSService {
	private static final Logger logger = LoggerFactory.getLogger(SMSService.class);

    private final String number;
    
	private final SMSRepository smsRepository;
	private final SMSRegistrationRepository smsRegistrationRepository;

	public SMSService(SMSRepository smsRepository, SMSRegistrationRepository smsRegistrationRepository, @Value("${telnyx.number}") String number) {
		this.smsRepository = smsRepository;
        this.smsRegistrationRepository = smsRegistrationRepository;
        this.number = number;
	}

	public int registerNumber(Long userId, String destNumber) {
        // Number format: "+15559876543"
        
        //// Check if a number isn't already registered
        //// and an active request for this user doesn't exist
        if( smsRepository.existsByUserIdAndVerifiedTrue(userId) || smsRegistrationRepository.existsByUserId(userId) )
            return -1;
        
        //// Keep number
        SMSEntity smsEntity = new SMSEntity(userId, destNumber);
        smsRepository.save(smsEntity);
        
        //// Generate code
        final var code = String.format("%06d", new SecureRandom().nextInt(1_000_000));
        
        //// Send request
        // apiKey loaded from java property telnyx.apiKey, or TELNYX_API_KEY env variable
        TelnyxClient client = TelnyxOkHttpClient.fromEnv();

        MessageSendParams params = MessageSendParams.builder()
            .from(number)
            .to(destNumber)
            .text(String.format("Votre code de confirmation: %s", code))
            .build();

        int maxRetries = 3;
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                MessageSendResponse response = client.messages().send(params);
                logger.info("Message sent: " + response);
                String idString = response.data().get().id().get();
                String status = response.data().get().to().get().getFirst().status().get().toString();
                UUID id = UUID.fromString(idString);
                
                SMSRegistrationEntity registration = new SMSRegistrationEntity(userId, code, id, status);
                smsRegistrationRepository.save(registration);
                
                return 0;
            } catch (RateLimitException e) {
                long waitMs = (long) Math.pow(2, attempt) * 1000;
                logger.info("Rate limited. Retrying in %dms...%n", waitMs);
                try {
                    Thread.sleep(waitMs);
                } catch (InterruptedException e2) {

                }

            } catch (TelnyxException e) {
                throw e;
            }
        }

		return 0;
	}

	public int validateNumber(Long userId, String number) {
        //// Check if an active request for this user exist
        if( !smsRegistrationRepository.existsByUserId(userId) )
            return -1;
        
        //// Check if code match
        SMSRegistrationEntity reg = smsRegistrationRepository.findByUserId(userId);

        // Code doesn't match
        final int check = reg.checkCode(number);
        switch( check ) {
            case -1:
                smsRegistrationRepository.save(reg);
                return -2;
            // No more attemps
            case -2:
                smsRegistrationRepository.delete(reg);
                return -3;
            default:
                break;
        }
        
        //// Code match
        // Update user entry
        SMSEntity smsEntity = smsRepository.findById(userId).get();
        smsEntity.setVerified();
        smsRepository.save(smsEntity);
        
        // Remove registration follow-up
        smsRegistrationRepository.delete(reg);
        
		return 0;
	}

	public int handleWebhook(String eventType, Instant occuredAt, String status, UUID eventId, UUID messageId, String phoneNumber) {
        SMSRegistrationEntity reg = smsRegistrationRepository.findByRequestId(messageId);
        if ( reg == null ) return -1;
        
        // Ignore event that occured before the one stored
        if ( reg.getOccuredAt().isAfter(occuredAt) ) {
            return -1;
        }
        
        reg.setEventType(eventType);
        reg.setOccuredAt(occuredAt);
        reg.setStatus(status);
        // Only need to keep track if the message has arrived
        // for user support
        smsRegistrationRepository.save(reg);
        
        return 0;
    }
}
