package ru.lukin.edododo.dto;

import java.util.Map;

public class DashboardStatsDto {
    private long totalActs;
    private long readyToSend;
    private long sentWaiting;
    private long signed;
    private long overdue;
    private long corrections;
    private long escalated;
    private long closed;
    private Map<String, Long> statusBreakdown;

    public long getTotalActs() {
        return totalActs;
    }

    public void setTotalActs(long totalActs) {
        this.totalActs = totalActs;
    }

    public long getReadyToSend() {
        return readyToSend;
    }

    public void setReadyToSend(long readyToSend) {
        this.readyToSend = readyToSend;
    }

    public long getSentWaiting() {
        return sentWaiting;
    }

    public void setSentWaiting(long sentWaiting) {
        this.sentWaiting = sentWaiting;
    }

    public long getSigned() {
        return signed;
    }

    public void setSigned(long signed) {
        this.signed = signed;
    }

    public long getOverdue() {
        return overdue;
    }

    public void setOverdue(long overdue) {
        this.overdue = overdue;
    }

    public long getCorrections() {
        return corrections;
    }

    public void setCorrections(long corrections) {
        this.corrections = corrections;
    }

    public long getEscalated() {
        return escalated;
    }

    public void setEscalated(long escalated) {
        this.escalated = escalated;
    }

    public long getClosed() {
        return closed;
    }

    public void setClosed(long closed) {
        this.closed = closed;
    }

    public Map<String, Long> getStatusBreakdown() {
        return statusBreakdown;
    }

    public void setStatusBreakdown(Map<String, Long> statusBreakdown) {
        this.statusBreakdown = statusBreakdown;
    }
}
