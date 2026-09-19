package com.eportfolium.karuta.service;

import org.springframework.stereotype.Service;

import com.eportfolium.karuta.repository.SMSRepository;

@Service
public class SMSService {

	private final SMSRepository smsRepository;

	public SMSService(SMSRepository smsRepository) {
		this.smsRepository = smsRepository;
	}

	public int registerNumber(String number) {

		return 0;
	}
}
