package com.eportfolium.karuta.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 *
 * @author Nobry
 */
@Entity
@Table(name = "sms_registration")
public class SMSRegistrationEntity {

    public enum ConfirmationAction {
        REGISTER,
        DEREGISTER
    }

    public SMSRegistrationEntity() {
        code = "";
    }
    
    public SMSRegistrationEntity( long userId, ConfirmationAction action, String code, UUID requestId, String status ) {
        this.userId = userId;
        this.action = action;
        this.code = code;
        this.requestId = requestId;
        this.occurredAt = Instant.now();
        this.status = status;
        this.eventType = "sent";
        this.attempt = 2;
    }
    
	@Id
	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "code", nullable = false)
	private final String code;
    
    public int checkCode( String code ) {
        if( this.code.equals(code) )
            return 0;
        attempt = attempt - 1;
        // Out of attempts
        if( attempt < 0 )
            return -2;
        return -1;
    }
    
    @Enumerated(EnumType.STRING)
    @Column(name = "confirm_action", nullable = false)
    private ConfirmationAction action;
    public ConfirmationAction getConfirmationAction() { return action; }

    /// Provider part
	@Column(name = "request_id", unique = true)
    private UUID requestId;
    
    // Keep track of message date since they can arrive out of order
	@Column(name = "occurred_at")
    private Instant occurredAt;
    
    public void setOccuredAt( Instant occurredAt ) { this.occurredAt = occurredAt; }
    public Instant getOccuredAt() { return occurredAt; }
    
	@Column(name = "event_type", nullable = false)
	private String eventType;

    public void setEventType( String eventType ) { this.eventType = eventType; }
    public String getEventType() { return eventType; }
    
	@Column(name = "status", nullable = false)
	private String status;

    public void setStatus( String status ) { this.status = status; }
    public String getStatus() { return status; }
    
  	@Column(name = "attempt", nullable = false)
	private int attempt;
    
    public void decreaseAttempt( ){ attempt = attempt - 1; }
    public int getAttempt( ){ return attempt; }

}
