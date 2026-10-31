package com.example.model;
import javax.persistence.*;
import java.time.LocalDateTime;

@Entity @Table(name="reservations")
@NamedEntityGraph(name="Reservation.details",attributeNodes={@NamedAttributeNode("salle"),@NamedAttributeNode("utilisateur")})
public class Reservation {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="salle_id",nullable=false) private Salle salle;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="utilisateur_id",nullable=false) private Utilisateur utilisateur;
    @Column(name="date_debut",nullable=false) private LocalDateTime dateDebut;
    @Column(name="date_fin",nullable=false) private LocalDateTime dateFin;
    @Version private int version;
    public Reservation() { }
    public Reservation(Salle salle,Utilisateur utilisateur,LocalDateTime debut,LocalDateTime fin) {
        this.salle=salle; this.utilisateur=utilisateur; this.dateDebut=debut; this.dateFin=fin;
    }
    public Long getId() { return id; }
    public Salle getSalle() { return salle; }
    public Utilisateur getUtilisateur() { return utilisateur; }
    public LocalDateTime getDateDebut() { return dateDebut; }
    public void setDateDebut(LocalDateTime debut) { this.dateDebut=debut; }
    public LocalDateTime getDateFin() { return dateFin; }
    public void setDateFin(LocalDateTime fin) { this.dateFin=fin; }
    public int getVersion() { return version; }
    @Override public String toString() { return "Reservation " + id + " | " + salle.getNom() + " | " + utilisateur.getNom() + " | " + dateDebut + " -> " + dateFin + " | version=" + version; }
}
