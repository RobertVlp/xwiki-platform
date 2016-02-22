/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package com.xpn.xwiki.store.hibernate.query;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hibernate.cfg.Configuration;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.xwiki.context.Execution;
import org.xwiki.context.ExecutionContext;
import org.xwiki.query.Query;
import org.xwiki.query.QueryException;
import org.xwiki.query.internal.DefaultQuery;
import org.xwiki.security.authorization.ContextualAuthorizationManager;
import org.xwiki.security.authorization.Right;
import org.xwiki.test.mockito.MockitoComponentMockingRule;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.store.XWikiHibernateBaseStore;
import com.xpn.xwiki.store.XWikiHibernateStore;
import com.xpn.xwiki.store.hibernate.HibernateSessionFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link HqlQueryExecutor}
 *
 * @version $Id$
 */
public class HqlQueryExecutorTest
{
    @Rule
    public MockitoComponentMockingRule<HqlQueryExecutor> mocker = new MockitoComponentMockingRule<>(
        HqlQueryExecutor.class);

    private ContextualAuthorizationManager authorization;

    private boolean hasProgrammingRight;

    /**
     * The component under test.
     */
    private HqlQueryExecutor executor;

    private XWikiHibernateStore store;

    @Before
    public void before() throws Exception
    {
        HibernateSessionFactory sessionFactory = this.mocker.getInstance(HibernateSessionFactory.class);
        when(sessionFactory.getConfiguration()).thenReturn(new Configuration());

        this.executor = this.mocker.getComponentUnderTest();
        this.authorization = this.mocker.getInstance(ContextualAuthorizationManager.class);

        when(this.authorization.hasAccess(Right.PROGRAM)).then(new Answer<Boolean>()
        {
            @Override
            public Boolean answer(InvocationOnMock invocation) throws Throwable
            {
                return hasProgrammingRight;
            }
        });

        this.hasProgrammingRight = true;

        // Actual Hibernate query

        Execution execution = this.mocker.getInstance(Execution.class);
        ExecutionContext executionContext = mock(ExecutionContext.class);
        when(execution.getContext()).thenReturn(executionContext);
        XWikiContext xwikiContext = mock(XWikiContext.class);
        when(executionContext.getProperty(XWikiContext.EXECUTIONCONTEXT_KEY)).thenReturn(xwikiContext);
        when(xwikiContext.getWikiId()).thenReturn("currentwikid");

        com.xpn.xwiki.XWiki xwiki = mock(com.xpn.xwiki.XWiki.class);
        when(xwikiContext.getWiki()).thenReturn(xwiki);
        this.store = mock(XWikiHibernateStore.class);
        when(xwiki.getHibernateStore()).thenReturn(store);
    }

    private void execute(String statement, Boolean withProgrammingRights) throws QueryException
    {
        this.hasProgrammingRight = withProgrammingRights != null ? withProgrammingRights : true;

        DefaultQuery query = new DefaultQuery(statement, Query.HQL, this.executor);
        if (withProgrammingRights != null) {
            query.checkCurrentAuthor(true);
        }

        this.executor.execute(query);
    }

    private void executeNamed(String name, Boolean withProgrammingRights) throws QueryException
    {
        this.hasProgrammingRight = withProgrammingRights;

        DefaultQuery query = new DefaultQuery(name, this.executor);
        if (withProgrammingRights != null) {
            query.checkCurrentAuthor(true);
        }

        this.executor.execute(query);
    }

    // Tests

    @Test
    public void completeShortStatementWhenEmpty()
    {
        assertEquals("select doc.fullName from XWikiDocument doc ", this.executor.completeShortFormStatement(""));
    }

    @Test
    public void completeShortStatementStartingWithWhere()
    {
        assertEquals("select doc.fullName from XWikiDocument doc where doc.author='XWiki.Admin'",
            this.executor.completeShortFormStatement("where doc.author='XWiki.Admin'"));
    }

    @Test
    public void completeShortStatementStartingWithFrom()
    {
        assertEquals("select doc.fullName from XWikiDocument doc , BaseObject obj where doc.fullName=obj.name "
            + "and obj.className='XWiki.MyClass'", this.executor.completeShortFormStatement(", BaseObject obj where "
            + "doc.fullName=obj.name and obj.className='XWiki.MyClass'"));
    }

    @Test
    public void completeShortStatementStartingWithOrderBy()
    {
        assertEquals("select doc.fullName from XWikiDocument doc order by doc.date desc",
            this.executor.completeShortFormStatement("order by doc.date desc"));
    }

    @Test
    public void completeShortStatementPassingAnAlreadyCompleteQuery()
    {
        assertEquals("select doc.fullName from XWikiDocument doc order by doc.date desc",
            this.executor
                .completeShortFormStatement("select doc.fullName from XWikiDocument doc order by doc.date desc"));
    }

    @Test
    public void completeShortStatementPassingAQueryOnSomethingElseThanADocument()
    {
        assertEquals("select lock.docId from XWikiLock as lock ",
            this.executor.completeShortFormStatement("select lock.docId from XWikiLock as lock "));
    }

    @Test
    public void setNamedParameter()
    {
        org.hibernate.Query query = mock(org.hibernate.Query.class);
        String name = "abc";
        Date value = new Date();
        this.executor.setNamedParameter(query, name, value);

        verify(query).setParameter(name, value);
    }

    @Test
    public void setNamedParameterList()
    {
        org.hibernate.Query query = mock(org.hibernate.Query.class);
        String name = "foo";
        List<String> value = Arrays.asList("one", "two", "three");
        this.executor.setNamedParameter(query, name, value);

        verify(query).setParameterList(name, value);
    }

    @Test
    public void setNamedParameterArray()
    {
        org.hibernate.Query query = mock(org.hibernate.Query.class);
        String name = "bar";
        Integer[] value = new Integer[] { 1, 2, 3 };
        this.executor.setNamedParameter(query, name, value);

        verify(query).setParameterList(name, value);
    }

    @Test
    public void populateParameters()
    {
        org.hibernate.Query hquery = mock(org.hibernate.Query.class);
        Query query = mock(Query.class);

        int offset = 13;
        when(query.getOffset()).thenReturn(offset);

        int limit = 7;
        when(query.getLimit()).thenReturn(limit);

        Map<String, Object> namedParameters = new HashMap<String, Object>();
        namedParameters.put("alice", 10);
        List<String> listValue = Collections.singletonList("yellow");
        namedParameters.put("bob", listValue);
        when(query.getNamedParameters()).thenReturn(namedParameters);

        this.executor.populateParameters(hquery, query);

        verify(hquery).setFirstResult(offset);
        verify(hquery).setMaxResults(limit);
        verify(hquery).setParameter("alice", 10);
        verify(hquery).setParameterList("bob", listValue);
    }

    @Test
    public void executeWhenStoreException() throws Exception
    {
        XWikiException exception = mock(XWikiException.class);
        when(exception.getMessage()).thenReturn("nestedmessage");

        when(this.store.executeRead(any(XWikiContext.class), any(XWikiHibernateBaseStore.HibernateCallback.class)))
            .thenThrow(exception);

        try {
            execute("statement", null);
            fail("Should have thrown an exception here");
        } catch (QueryException expected) {
            assertEquals("Exception while executing query. Query statement = [statement]", expected.getMessage());
            // Verify nested exception!
            assertEquals("nestedmessage", expected.getCause().getMessage());
        }
    }

    // Allowed

    @Test
    public void executeShortWhereHQLQueryWithProgrammingRights() throws QueryException
    {
        execute("where doc.space='Main'", true);
    }

    @Test
    public void executeShortFromHQLQueryWithProgrammingRights() throws QueryException
    {
        execute(", BaseObject as obj", true);
    }

    @Test
    public void executeCompleteHQLQueryWithProgrammingRights() throws QueryException
    {
        execute("select u from XWikiDocument as doc", true);

    }

    @Test
    public void executeNamedQueryWithProgrammingRights() throws QueryException
    {
        executeNamed("somename", true);
    }

    @Test
    public void executeShortWhereHQLQueryWithoutProgrammingRights() throws QueryException
    {
        execute("where doc.space='Main'", false);
    }

    @Test
    public void executeShortFromHQLQueryWithoutProgrammingRights() throws QueryException
    {
        execute(", BaseObject as obj", false);
    }

    // Not allowed

    @Test
    public void executeWhenNotAllowedSelect() throws Exception
    {
        try {
            execute("select notallowed.name from NotAllowedTable notallowed", false);
            fail("Should have thrown an exception here");
        } catch (QueryException expected) {
            assertEquals("The query requires programming right."
                + " Query statement = [select notallowed.name from NotAllowedTable notallowed]", expected.getMessage());
        }
    }

    @Test
    public void executeDeleteWithoutProgrammingRight() throws Exception
    {
        try {
            execute("delete from XWikiDocument as doc", false);
            fail("Should have thrown an exception here");
        } catch (QueryException expected) {
            assertEquals("The query requires programming right. Query statement = [delete from XWikiDocument as doc]",
                expected.getMessage());
        }
    }

    @Test
    public void executeNamedQueryWithoutProgrammingRight() throws Exception
    {
        try {
            executeNamed("somename", false);
            fail("Should have thrown an exception here");
        } catch (QueryException expected) {
            assertEquals("Named queries requires programming right. Named query = [somename]", expected.getMessage());
        }
    }

    @Test
    public void executeUpdateWithoutProgrammingRight() throws Exception
    {
        try {
            execute("update XWikiDocument set name='name'", false);
            fail("Should have thrown an exception here");
        } catch (QueryException expected) {
            assertEquals(
                "The query requires programming right. Query statement = [update XWikiDocument set name='name']",
                expected.getMessage());
        }
    }
}
