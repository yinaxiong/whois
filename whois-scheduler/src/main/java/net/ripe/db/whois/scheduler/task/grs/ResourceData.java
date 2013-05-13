package net.ripe.db.whois.scheduler.task.grs;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import net.ripe.db.whois.common.domain.CIString;
import net.ripe.db.whois.common.domain.Ipv4Resource;
import net.ripe.db.whois.common.domain.Ipv6Resource;
import net.ripe.db.whois.common.etree.IntervalMap;
import net.ripe.db.whois.common.etree.NestedIntervalMap;
import net.ripe.db.whois.common.rpsl.ObjectType;
import net.ripe.db.whois.common.rpsl.RpslObject;
import org.slf4j.Logger;

import javax.annotation.concurrent.Immutable;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.Collections;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static net.ripe.db.whois.common.domain.CIString.ciString;

@Immutable
class ResourceData {
    private static final Object PRESENT = new Object();

    private static final Set<ObjectType> RESOURCE_TYPES = Sets.newEnumSet(Lists.newArrayList(ObjectType.AUT_NUM, ObjectType.INETNUM, ObjectType.INET6NUM), ObjectType.class);

    private final Set<CIString> autNums;
    private final IntervalMap<Ipv4Resource, Object> inetRanges;
    private final int nrInetRanges;
    private final IntervalMap<Ipv6Resource, Object> inet6Ranges;
    private final int nrInet6Ranges;


    static ResourceData unknown(final GrsSource grsSource) {
        return new ResourceData(grsSource, Collections.<CIString>emptySet(), new NestedIntervalMap<Ipv4Resource, Object>(), new NestedIntervalMap<Ipv6Resource, Object>());
    }

    static ResourceData loadFromFile(final GrsSource grsSource, final File file) {
        try {
            return loadFromScanner(grsSource, new Scanner(file));
        } catch (FileNotFoundException e) {
            throw new IllegalArgumentException("File not found", e);
        }
    }

    static ResourceData loadFromScanner(final GrsSource grsSource, final Scanner scanner) {
        try {
            return load(grsSource, scanner);
        } finally {
            scanner.close();
        }
    }

    private static ResourceData load(final GrsSource grsSource, final Scanner scanner) {
        scanner.useDelimiter("\n");

        final Logger logger = grsSource.getLogger();
        final Set<String> allowedStatusses = Sets.newHashSet("allocated", "assigned");
        final Pattern linePattern = Pattern.compile("^([a-zA-Z]+)\\|(.*?)\\|(.*?)\\|(.*?)\\|(.*?)\\|(.*?)\\|(.*?)(?:\\|.*|$)");

        return new Callable<ResourceData>() {
            final Set<CIString> autNums = Sets.newHashSet();
            final IntervalMap<Ipv4Resource, Object> inetnums = new NestedIntervalMap<Ipv4Resource, Object>();
            final IntervalMap<Ipv6Resource, Object> inet6nums = new NestedIntervalMap<Ipv6Resource, Object>();

            @Override
            public ResourceData call() {
                final String expectedSource = grsSource.getSource().replace("-GRS", "").toLowerCase();

                while (scanner.hasNext()) {
                    final String line = scanner.next();
                    handleLine(expectedSource, line);
                }

                return new ResourceData(grsSource, autNums, inetnums, inet6nums);
            }

            private void handleLine(final String expectedSource, final String line) {
                final Matcher matcher = linePattern.matcher(line);
                if (!matcher.matches()) {
                    logger.debug("Skipping: {}", line);
                    return;
                }

                final String source = matcher.group(1);
                final String cc = matcher.group(2);
                final String type = matcher.group(3).toLowerCase();
                final String start = matcher.group(4);
                final String value = matcher.group(5);
                final String status = matcher.group(7).toLowerCase();

                if (!source.toLowerCase().contains(expectedSource)) {
                    logger.debug("Ignoring source '{}': {}", source, line);
                    return;
                }

                if (cc.indexOf('*') != -1) {
                    logger.debug("Ignoring country code '{}': {}", cc, line);
                    return;
                }

                if (!allowedStatusses.contains(status)) {
                    logger.debug("Ignoring status '{}': {}", status, line);
                    return;
                }

                try {
                    if (type.equals("ipv4")) {
                        createIpv4Resource(start, value);
                    } else if (type.equals("ipv6")) {
                        createIpv6Resource(start, value);
                    } else if (type.equals("asn")) {
                        createAutNum(start, value);
                    } else {
                        logger.debug("Unsupported type '{}': {}", type, line);
                    }
                } catch (RuntimeException ignored) {
                    logger.warn("Unexpected '{}-{}': {}", ignored, ignored.getMessage(), line);
                }
            }

            private void createAutNum(final String start, final String value) {
                final int startNum = Integer.parseInt(start);
                final int count = Integer.parseInt(value);
                for (int i = 0; i < count; i++) {
                    autNums.add(ciString(String.format("AS%s", startNum + i)));
                }
            }

            private void createIpv4Resource(final String start, final String value) {
                final long begin = Ipv4Resource.parse(start).begin();
                final long end = begin + (Long.parseLong(value) - 1);
                final Ipv4Resource ipv4Resource = new Ipv4Resource(begin, end);
                inetnums.put(ipv4Resource, PRESENT);
            }

            private void createIpv6Resource(final String start, final String value) {
                final Ipv6Resource ipv6Resource = Ipv6Resource.parse(String.format("%s/%s", start, value));
                inet6nums.put(ipv6Resource, PRESENT);
            }
        }.call();
    }

    private ResourceData(final GrsSource grsSource, final Set<CIString> autNums, final IntervalMap<Ipv4Resource, Object> inetRanges, final IntervalMap<Ipv6Resource, Object> inet6Ranges) {
        this.autNums = autNums;
        this.inetRanges = inetRanges;
        this.inet6Ranges = inet6Ranges;
        this.nrInetRanges = inetRanges.findExactAndAllMoreSpecific(Ipv4Resource.parse("0/0")).size();
        this.nrInet6Ranges = inet6Ranges.findExactAndAllMoreSpecific(Ipv6Resource.parse("::0/0")).size();

        grsSource.getLogger().info("Resources: {}", String.format("asn: %5d; ipv4: %5d; ipv6: %5d", getNrAutNums(), getNrInetnums(), getNrInet6nums()));
    }

    int getNrAutNums() {
        return autNums.size();
    }

    int getNrInetnums() {
        return nrInetRanges;
    }

    int getNrInet6nums() {
        return nrInet6Ranges;
    }

    boolean isEmpty() {
        return getNrAutNums() == 0 && getNrInetnums() == 0 && getNrInet6nums() == 0;
    }

    boolean isMaintainedByRir(final ObjectType objectType, final CIString pkey) {
        try {
            switch (objectType) {
                case AUT_NUM:
                    return autNums.contains(pkey);
                case INETNUM:
                    return !inetRanges.findExact(Ipv4Resource.parse(pkey)).isEmpty();
                case INET6NUM:
                    return !inet6Ranges.findExact(Ipv6Resource.parse(pkey)).isEmpty();
                default:
                    return true;
            }
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    boolean isMaintainedInRirSpace(final RpslObject rpslObject) {
        return isMaintainedInRirSpace(rpslObject.getType(), rpslObject.getKey());
    }

    boolean isMaintainedInRirSpace(final ObjectType objectType, final CIString pkey) {
        try {
            switch (objectType) {
                case AUT_NUM:
                    return autNums.contains(pkey);
                case INETNUM:
                    return !inetRanges.findExactOrFirstLessSpecific(Ipv4Resource.parse(pkey)).isEmpty();
                case INET6NUM:
                    return !inet6Ranges.findExactOrFirstLessSpecific(Ipv6Resource.parse(pkey)).isEmpty();
                default:
                    return true;
            }
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    Set<ObjectType> getResourceTypes() {
        return RESOURCE_TYPES;
    }
}
