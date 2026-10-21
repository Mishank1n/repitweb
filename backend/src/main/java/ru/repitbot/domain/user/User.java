package ru.repitbot.domain.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "users")
public class User {

    public static final int MAX_NAME = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "login", nullable = false, unique = true)
    private String login;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Role role;

    @Column(name = "timezone", nullable = false)
    private String timezone;

    @Column(name = "consent_at")
    private Instant consentAt;

    @Column(name = "delete_requested_at")
    private Instant deleteRequestedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected User() {
    }

    /** login — уже проверенный и приведённый к нижнему регистру ({@link Credentials#normalizeLogin}). */
    public User(String login, String passwordHash, Role role, String timezone) {
        this.login = login;
        this.passwordHash = passwordHash;
        this.role = role;
        this.timezone = timezone;
    }

    /** Имя и фамилия, как человек их ввёл. Пустое имя не принимается, лишние пробелы убираются. */
    public void rename(String firstName, String lastName) {
        String first = clean(firstName);
        if (first == null) {
            throw new IllegalArgumentException("Введите имя");
        }
        this.firstName = first;
        this.lastName = clean(lastName);
    }

    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String displayName() {
        return lastName == null || lastName.isBlank() ? firstName : firstName + " " + lastName;
    }

    public boolean hasConsent() {
        return role == Role.TUTOR || consentAt != null;
    }

    public void giveConsent() {
        this.consentAt = Instant.now();
        this.deleteRequestedAt = null;
    }

    public void requestDeletion() {
        this.consentAt = null;
        this.deleteRequestedAt = Instant.now();
    }

    private static String clean(String s) {
        if (s == null) {
            return null;
        }
        String t = s.strip().replaceAll("\\s+", " ");
        if (t.isEmpty()) {
            return null;
        }
        return t.length() > MAX_NAME ? t.substring(0, MAX_NAME) : t;
    }

    public Long getId() {
        return id;
    }

    public String getLogin() {
        return login;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public Role getRole() {
        return role;
    }

    public String getTimezone() {
        return timezone;
    }

    public Instant getConsentAt() {
        return consentAt;
    }

    public Instant getDeleteRequestedAt() {
        return deleteRequestedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
