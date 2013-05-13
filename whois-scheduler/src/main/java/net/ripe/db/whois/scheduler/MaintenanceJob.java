package net.ripe.db.whois.scheduler;

import net.ripe.db.whois.update.domain.Origin;

public class MaintenanceJob implements Origin {
    private final String id;

    public MaintenanceJob(final String id) {
        this.id = id;
    }

    @Override
    public boolean isDefaultOverride() {
        return true;
    }

    @Override
    public boolean allowRipeOperations() {
        return true;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getFrom() {
        return id;
    }

    @Override
    public String getResponseHeader() {
        return "Maintenance job: " + getFrom() + "\n";
    }

    @Override
    public String getNotificationHeader() {
        return getResponseHeader();
    }

    @Override
    public String getName() {
        return "maintenance job";
    }

    @Override
    public String toString() {
        return "MaintenanceJob(" + getId() + ")";
    }
}
