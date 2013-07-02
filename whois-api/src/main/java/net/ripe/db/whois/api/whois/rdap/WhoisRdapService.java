package net.ripe.db.whois.api.whois.rdap;

import net.ripe.db.whois.api.whois.ApiResponseHandler;
import net.ripe.db.whois.api.whois.StreamingMarshal;
import net.ripe.db.whois.api.whois.WhoisService;
import net.ripe.db.whois.api.whois.domain.Parameters;
import net.ripe.db.whois.api.whois.domain.WhoisResources;
import net.ripe.db.whois.common.DateTimeProvider;
import net.ripe.db.whois.common.dao.RpslObjectDao;
import net.ripe.db.whois.common.dao.RpslObjectUpdateDao;
import net.ripe.db.whois.common.domain.ResponseObject;
import net.ripe.db.whois.common.rpsl.RpslObject;
import net.ripe.db.whois.common.source.SourceContext;
import net.ripe.db.whois.query.handler.QueryHandler;
import net.ripe.db.whois.query.query.Query;
import net.ripe.db.whois.query.query.QueryFlag;
import net.ripe.db.whois.update.handler.UpdateRequestHandler;
import net.ripe.db.whois.update.log.LoggerContext;
import org.codehaus.enunciate.jaxrs.TypeHint;
import org.codehaus.enunciate.modules.jersey.ExternallyManagedLifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.InetAddress;

@ExternallyManagedLifecycle
@Component
@Path("/")
public class WhoisRdapService extends WhoisService {

    @Autowired
    public WhoisRdapService(final DateTimeProvider dateTimeProvider, final UpdateRequestHandler updateRequestHandler, final LoggerContext loggerContext, final RpslObjectDao rpslObjectDao, final RpslObjectUpdateDao rpslObjectUpdateDao, final SourceContext sourceContext, final QueryHandler queryHandler) {
        super(dateTimeProvider, updateRequestHandler,loggerContext, rpslObjectDao, rpslObjectUpdateDao, sourceContext, queryHandler);
    }

    @GET
    @TypeHint(WhoisResources.class)
    @Produces({MediaType.APPLICATION_JSON})//,MediaType.APPLICATION_JSON})
    @Path("/{objectType}/{key:.*}")
    public Response lookup(
            @Context final HttpServletRequest request,
            @PathParam("objectType") final String objectType,
            @PathParam("key") final String key) {
        /* RDAP object types do not map directly to whois object
         * types, so translate accordingly here for the remaining
         * object types as they are implemented. */
        
        String whoisObjectType = objectType;
        String whoisKey        = key;
        
        if (objectType.equals("autnum")) {
            whoisObjectType = (asnExists(key)) ? "aut-num" : "as-block";
            whoisKey = "AS" + key;
        }

        Response res = lookupObject(request, source(), whoisObjectType, 
                                    whoisKey, false);

        return res;
    }

    protected Response handleQueryAndStreamResponse(final Query query, final HttpServletRequest request, final InetAddress remoteAddress, final int contextId, @Nullable final Parameters parameters) {
        final StreamingMarshal streamingMarshal = new RdapStreamingMarshalJson();

        RdapStreamingOutput rso = new RdapStreamingOutput(streamingMarshal,queryHandler,parameters,query,remoteAddress,contextId);

        return Response.ok(rso).build();
    }

    private String source() {
        return this.sourceContext
                   .getWhoisSlaveSource().getName().toString();
    }

    // TODO: [AH] hierarchical lookups return the encompassing range if no direct hit
    protected Response lookupObject(
            final HttpServletRequest request,
            final String source,
            final String objectTypeString,
            final String key,
            final boolean isGrs) {
        final Query query = Query.parse(String.format("%s %s %s %s %s %s %s %s %s",
                QueryFlag.NO_GROUPING.getLongFlag(),
                QueryFlag.NO_REFERENCED.getLongFlag(),
                QueryFlag.SOURCES.getLongFlag(),
                source,
                QueryFlag.SELECT_TYPES.getLongFlag(),
                objectTypeString,
                QueryFlag.SHOW_TAG_INFO.getLongFlag(),
                QueryFlag.NO_FILTERING.getLongFlag(),
                key));

        checkForInvalidSource(source, isGrs);

        return handleQuery(query, source, key, request, null);
    }

    private boolean asnExists(String asn) {
        /* todo: There is probably an easier/better way to do this. */
        final String qstring =
            String.format("%s %s %s %s %s %s",
                QueryFlag.SOURCES.getLongFlag(),       source(),
                QueryFlag.SELECT_TYPES.getLongFlag(),  "aut-num",
                QueryFlag.SHOW_TAG_INFO.getLongFlag(), "AS" + asn
            );
        final Query query = Query.parse(qstring);
        final boolean[] found_asn = { false };
        final int contextId = 
            System.identityHashCode(Thread.currentThread());
        queryHandler.streamResults(query, InetAddress.getLoopbackAddress(),
                                   contextId, new ApiResponseHandler() {

            @Override
            public void handle(final ResponseObject responseObject) {
                if ((responseObject instanceof RpslObject)
                        && ((RpslObject) responseObject)
                                .getType().getName().equals("aut-num")) {
                    found_asn[0] = true;
                }
            }
        });
        return found_asn[0];
    }
}
