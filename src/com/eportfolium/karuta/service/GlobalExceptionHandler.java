package com.eportfolium.karuta.service;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.eportfolium.karuta.rest.RestWebApplicationException;

@RestControllerAdvice
public class GlobalExceptionHandler {
	@ExceptionHandler(RestWebApplicationException.class)
	public ResponseEntity<String> handleUnauthorized(RestWebApplicationException ex) {
		return ResponseEntity.status(HttpStatus.valueOf(ex.getStatus().name())).body(ex.getMessage());
	}
}
