package net.ripe.db.whois.update.handler.validator.common;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import net.ripe.db.whois.common.dao.RpslObjectUpdateDao;
import net.ripe.db.whois.common.domain.CIString;
import net.ripe.db.whois.common.rpsl.ObjectMessages;
import net.ripe.db.whois.common.rpsl.RpslAttribute;
import net.ripe.db.whois.common.rpsl.RpslObject;
import net.ripe.db.whois.update.domain.Action;
import net.ripe.db.whois.update.domain.PreparedUpdate;
import net.ripe.db.whois.update.domain.UpdateContext;
import net.ripe.db.whois.update.domain.UpdateMessages;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class ReferencedObjectsExistValidatorTest {
    @Mock PreparedUpdate update;
    @Mock UpdateContext updateContext;

    @Mock RpslObjectUpdateDao rpslObjectUpdateDao;
    @InjectMocks ReferencedObjectsExistValidator subject;

    private RpslObject object;

    @Before
    public void setUp() throws Exception {
        object = RpslObject.parse("mntner: TST-MNT\nadmin-c: ADMIN_NC");
        when(update.getUpdatedObject()).thenReturn(object);
    }

    @Test
    public void getActions() {
        assertThat(subject.getActions(), containsInAnyOrder(Action.CREATE, Action.MODIFY));
    }

    @Test
    public void validate_no_invalid_references() {
        when(rpslObjectUpdateDao.getInvalidReferences(object)).thenReturn(Collections.<RpslAttribute, Set<String>>emptyMap());
        subject.validate(update, updateContext);

        verify(updateContext).getMessages(update);
        verifyNoMoreInteractions(updateContext);
    }

    @Test
    public void validate_invalid_references() {
        final RpslAttribute invalidAttribute = object.getAttributes().get(1);

        final Map<RpslAttribute, Set<String>> invalidReferences = Maps.newHashMap();
        final Set<String> result = Sets.newLinkedHashSet();
        for (final CIString value : invalidAttribute.getCleanValues()) {
            result.add(value.toString());
        }

        invalidReferences.put(invalidAttribute, result);

        when(updateContext.getMessages(update)).thenReturn(new ObjectMessages());
        when(rpslObjectUpdateDao.getInvalidReferences(object)).thenReturn(invalidReferences);
        subject.validate(update, updateContext);

        verify(updateContext).addMessage(update, invalidAttribute, UpdateMessages.unknownObjectReferenced("ADMIN_NC"));
    }
}
