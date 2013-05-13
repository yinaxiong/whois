package net.ripe.db.whois.query.support;

import net.ripe.db.whois.common.profiles.TestingProfile;
import net.ripe.db.whois.query.acl.PersonalObjectAccounting;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

@TestingProfile
@Component
public class TestPersonalObjectAccounting implements PersonalObjectAccounting {
    private Map<InetAddress, Integer> queriedPersonalObjects = new HashMap<InetAddress, Integer>();

    @Override
    public int getQueriedPersonalObjects(final InetAddress remoteAddress) {
        final Integer count = queriedPersonalObjects.get(remoteAddress);
        if (count == null) {
            return 0;
        }

        return count;
    }

    @Override
    public int accountPersonalObject(final InetAddress remoteAddress, final int amount) {
        Integer count = queriedPersonalObjects.get(remoteAddress);
        if (count == null) {
            count = amount;
        } else {
            count += amount;
        }

        queriedPersonalObjects.put(remoteAddress, count);
        return count;
    }

    @Override
    public void resetAccounting() {
        queriedPersonalObjects = new HashMap<InetAddress, Integer>();
    }
}
