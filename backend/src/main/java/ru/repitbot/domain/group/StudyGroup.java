package ru.repitbot.domain.group;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Группа учеников. Индивидуальный ученик = группа из одного человека. */
@Entity
@Table(name = "study_groups")
public class StudyGroup {

    public static final int MAX_NAME_LENGTH = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = MAX_NAME_LENGTH)
    private String name;

    public static final int MAX_URL_LENGTH = 500;
    public static final int MAX_ADDRESS_LENGTH = 300;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    /** Где проходят занятия; null — ещё не задано. */
    @Enumerated(EnumType.STRING)
    @Column(name = "location_type")
    private LocationType locationType;

    @Column(name = "meeting_url", length = MAX_URL_LENGTH)
    private String meetingUrl;

    @Column(name = "address", length = MAX_ADDRESS_LENGTH)
    private String address;

    protected StudyGroup() {
    }

    public StudyGroup(String name) {
        this.name = name;
    }

    public void rename(String name) {
        this.name = name;
    }

    public void setOnline(String meetingUrl) {
        this.locationType = LocationType.ONLINE;
        this.meetingUrl = meetingUrl;
        this.address = null;
    }

    public void setOffline(String address) {
        this.locationType = LocationType.OFFLINE;
        this.address = address;
        this.meetingUrl = null;
    }

    public LocationType getLocationType() {
        return locationType;
    }

    public String getMeetingUrl() {
        return meetingUrl;
    }

    public String getAddress() {
        return address;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
