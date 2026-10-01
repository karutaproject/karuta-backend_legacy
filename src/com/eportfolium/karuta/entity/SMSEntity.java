package com.eportfolium.karuta.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "sms_table")
public class SMSEntity {
    public SMSEntity() {}
    
    public SMSEntity( long userId, String phone ) {
        this.userId = userId;
        this.phone = phone;
        this.verified = false;
        this.date = Instant.now();
    }
    
	@Id
	@Column(name = "user_id", nullable = false)
	private Long userId;
    
    public Long getUserId() { return userId; }

	@Column(name = "phone_number", length = 20, nullable = false)
	private String phone;
    
    public String getPhone() { return phone; }

	@Column(name = "is_verified")
	private boolean verified;
    
    public void setVerified() {
        verified = true;
        date = Instant.now();
    }
    public boolean getVerified() { return verified; }

    /// is_verified = true -> Activation date
    /// is_verified = false -> Demand date (query limit)
	@Column(name = "date")
	private Instant date;
    
    public Instant getDate() { return date; }
}
