package com.eportfolium.karuta.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "sms_table")
public class SMSEntity {
	@Id
	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "phone_number", length = 20, nullable = false)
	private String phone;

	@Column(name = "is_verified")
	private boolean enabled;

	@Column(name = "date")
	private Instant date;
}
