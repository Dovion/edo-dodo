package ru.lukin.edododo.dto;

public class StatusUpdateRequest {
    private String status;
    private String comment;

    public StatusUpdateRequest() {
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}
