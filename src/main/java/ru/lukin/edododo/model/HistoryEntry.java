package ru.lukin.edododo.model;

import java.time.Instant;

public class HistoryEntry {
    private Instant timestamp;
    private String oldStatus;
    private String newStatus;
    private String comment;

    public HistoryEntry() {
    }

    public HistoryEntry(Instant timestamp, String oldStatus, String newStatus, String comment) {
        this.timestamp = timestamp;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
        this.comment = comment;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public String getOldStatus() {
        return oldStatus;
    }

    public void setOldStatus(String oldStatus) {
        this.oldStatus = oldStatus;
    }

    public String getNewStatus() {
        return newStatus;
    }

    public void setNewStatus(String newStatus) {
        this.newStatus = newStatus;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}