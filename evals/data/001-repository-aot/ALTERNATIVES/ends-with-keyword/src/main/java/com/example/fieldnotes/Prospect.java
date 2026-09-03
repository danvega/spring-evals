package com.example.fieldnotes;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Prospect {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String company;
    private String email;

    protected Prospect() {
    }

    public Prospect(String company, String email) {
        this.company = company;
        this.email = email;
    }

    public Long getId() {
        return id;
    }

    public String getCompany() {
        return company;
    }

    public String getEmail() {
        return email;
    }
}
