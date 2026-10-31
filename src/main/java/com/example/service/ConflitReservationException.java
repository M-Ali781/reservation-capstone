package com.example.service;
public class ConflitReservationException extends RuntimeException {
    public ConflitReservationException() { super("La salle est deja reservee sur ce creneau"); }
}
