package com.example.model;
import javax.persistence.*;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

@Entity @Table(name="utilisateurs") @Cacheable @Cache(usage=CacheConcurrencyStrategy.READ_WRITE)
public class Utilisateur {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false, unique=true) private String email;
    @Column(nullable=false) private String nom;
    @Version private int version;
    public Utilisateur() { }
    public Utilisateur(String nom, String email) { this.nom=nom; this.email=email; }
    public Long getId() { return id; }
    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom=nom; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email=email; }
    public int getVersion() { return version; }
}
