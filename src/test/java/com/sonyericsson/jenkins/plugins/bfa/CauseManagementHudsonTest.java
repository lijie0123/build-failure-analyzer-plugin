/*
 * The MIT License
 *
 * Copyright 2014 Sony Mobile Communications Inc. All rights reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.sonyericsson.jenkins.plugins.bfa;

import com.gargoylesoftware.htmlunit.html.DomElement;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlInput;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlTable;
import com.gargoylesoftware.htmlunit.html.HtmlTableRow;
import com.sonyericsson.jenkins.plugins.bfa.db.KnowledgeBase;
import com.sonyericsson.jenkins.plugins.bfa.db.MongoDBKnowledgeBase;
import com.sonyericsson.jenkins.plugins.bfa.model.FailureCause;
import com.sonyericsson.jenkins.plugins.bfa.model.FailureCauseModification;
import com.sonyericsson.jenkins.plugins.bfa.model.ScannerJobProperty;
import com.sonyericsson.jenkins.plugins.bfa.model.indication.BuildLogIndication;
import com.sonyericsson.jenkins.plugins.bfa.test.utils.Whitebox;
import hudson.Util;
import hudson.model.FreeStyleProject;
import hudson.util.Secret;
import org.apache.commons.lang.StringUtils;
import org.jvnet.hudson.test.HudsonTestCase;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.http.HttpSession;
import java.text.DateFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Hudson tests for {@link CauseManagement}.
 *
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public class CauseManagementHudsonTest extends HudsonTestCase {

    private static final int NAME_CELL = 0;
    private static final int CATEGORY_CELL = 1;
    private static final int DESCRIPTION_CELL = 2;
    private static final int COMMENT_CELL = 3;
    private static final int MODIFIED_CELL = 4;
    private static final int LAST_SEEN_CELL = 5;

    /**
     * Tests {@link com.sonyericsson.jenkins.plugins.bfa.CauseManagement#isUnderTest()}.
     */
    public void testIsUnderTest() {
        assertTrue(CauseManagement.getInstance().isUnderTest());
    }

    /**
     * Verifies that the table on the {@link CauseManagement} page displays all causes with description and that
     * one of them can be navigated to and a valid edit page for that cause is shown.
     *
     * @throws Exception if so.
     */
    public void testTableViewNavigation() throws Exception {
        KnowledgeBase kb = PluginImpl.getInstance().getKnowledgeBase();

        //Overriding isEnableStatistics in order to display all fields on the management page:
        KnowledgeBase mockKb = spy(kb);
        when(mockKb.isEnableStatistics()).thenReturn(true);
        Whitebox.setInternalState(PluginImpl.getInstance(), "knowledgeBase", mockKb);

        List<String> myCategories = new LinkedList<String>();
        myCategories.add("myCtegory");

        //CS IGNORE MagicNumber FOR NEXT 5 LINES. REASON: TestData.
        Date endOfWorld = new Date(1356106375000L);
        Date birthday = new Date(678381175000L);
        Date millenniumBug = new Date(946681200000L);
        Date pluginReleased = new Date(1351724400000L);

        FailureCause cause = new FailureCause(null, "SomeName", "A Description", "Some comment",
                endOfWorld, myCategories, null,
                Collections.singletonList(new FailureCauseModification("user", birthday)));
        cause.addIndication(new BuildLogIndication("."));
        kb.addCause(cause);
        cause = new FailureCause(null, "SomeOtherName", "A Description", "Another comment",
                millenniumBug, myCategories, null,
                Collections.singletonList(new FailureCauseModification("user", pluginReleased)));
        cause.addIndication(new BuildLogIndication("."));
        kb.addCause(cause);

        WebClient web = createWebClient();
        HtmlPage page = web.goTo(CauseManagement.URL_NAME);
        HtmlTable table = (HtmlTable)page.getElementById("failureCausesTable");

        Collection<FailureCause> expectedCauses = kb.getShallowCauses();

        int rowCount = table.getRowCount();
        assertEquals(expectedCauses.size() + 1, rowCount);
        Iterator<FailureCause> causeIterator = expectedCauses.iterator();

        FailureCause firstCause = null;

        for (int i = 1; i < rowCount; i++) {
            assertTrue(causeIterator.hasNext());
            FailureCause c = causeIterator.next();
            HtmlTableRow row = table.getRow(i);
            String name = row.getCell(NAME_CELL).getTextContent();
            String categories = row.getCell(CATEGORY_CELL).getTextContent();
            String description = row.getCell(DESCRIPTION_CELL).getTextContent();
            String comment = row.getCell(COMMENT_CELL).getTextContent();
            String modified = row.getCell(MODIFIED_CELL).getTextContent();
            String lastSeen = row.getCell(LAST_SEEN_CELL).getTextContent();
            assertEquals(c.getName(), name);
            assertEquals(c.getCategoriesAsString(), categories);
            assertEquals(c.getDescription(), description);
            assertEquals(c.getComment(), comment);
            assertEquals("Modified date should be visible", DateFormat.getDateTimeInstance(
                    DateFormat.SHORT, DateFormat.SHORT).format(c.getLatestModification().getTime())
                    + " by user", modified);
            assertEquals("Last seen date should be visible",
                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                    .format(c.getLastOccurred()), lastSeen);
            if (i == 1) {
                firstCause = c;
            }
        }

        //The table looks ok, now lets see if we can navigate correctly.

        assertNotNull(firstCause);

        HtmlAnchor firstCauseLink = (HtmlAnchor)table.getCellAt(1, 0).getFirstChild();
        HtmlPage editPage = firstCauseLink.click();

        verifyCorrectCauseEditPage(firstCause, editPage);
    }

    /**
     * Tests that the "new cause" link on the page navigates to a correct page.
     * @throws Exception if so.
     */
    public void testNewNavigation() throws Exception {

        WebClient web = createWebClient();
        HtmlPage page = web.goTo(CauseManagement.URL_NAME);

        HtmlAnchor newLink = page.getAnchorByHref(CauseManagement.NEW_CAUSE_DYNAMIC_ID);
        HtmlPage editPage = newLink.click();

        verifyCorrectCauseEditPage(new FailureCause(
                        CauseManagement.NEW_CAUSE_NAME,
                        CauseManagement.NEW_CAUSE_DESCRIPTION, ""),
                editPage);
    }

    /**
     * Makes a modification to a {@link FailureCause} and verifies that the modification date was updated.
     * @throws Exception if something goes wrong
     */
    public void testMakeModificationUpdatesDate() throws Exception {
        List<FailureCauseModification> modifications = new LinkedList<FailureCauseModification>();
        modifications.add(new FailureCauseModification("unknown", new Date(1)));
        FailureCause cause = new FailureCause(null, "SomeName", "A Description", "Some comment",
                null, "", null, modifications);
        cause.addIndication(new BuildLogIndication("."));
        PluginImpl.getInstance().getKnowledgeBase().addCause(cause);

        WebClient web = createWebClient();
        HtmlPage page = web.goTo(CauseManagement.URL_NAME);

        HtmlTable table = (HtmlTable)page.getElementById("failureCausesTable");
        HtmlTableRow row = table.getRow(1);
        final String firstModification = row.getCell(MODIFIED_CELL).getTextContent();

        HtmlAnchor firstCauseLink = (HtmlAnchor)table.getCellAt(1, 0).getFirstChild();
        HtmlPage editPage = firstCauseLink.click();

        editPage.getElementByName("_.comment").setTextContent("new comment");

        HtmlForm form = editPage.getFormByName("causeForm");
        page = submit(form);

        table = (HtmlTable)page.getElementById("failureCausesTable");
        row = table.getRow(1);
        final String secondModification = row.getCell(MODIFIED_CELL).getTextContent();

        assertThat(secondModification, not(equalTo(firstModification)));
        assertThat(secondModification, not(equalTo(StringUtils.EMPTY)));
    }

    /**
     * Makes a modification to a {@link FailureCause} and verifies that the modification list was updated.
     * @throws Exception if something goes wrong
     */
    public void testMakeModificationUpdatesModificationList() throws Exception {
        List<FailureCauseModification> modifications = new LinkedList<FailureCauseModification>();
        modifications.add(new FailureCauseModification("unknown", new Date(1)));
        FailureCause cause = new FailureCause(null, "SomeName", "A Description", "Some comment",
                null, "", null, modifications);
        cause.addIndication(new BuildLogIndication("."));
        PluginImpl.getInstance().getKnowledgeBase().addCause(cause);

        WebClient web = createWebClient();
        HtmlPage page = web.goTo(CauseManagement.URL_NAME);

        HtmlTable table = (HtmlTable)page.getElementById("failureCausesTable");

        HtmlAnchor firstCauseLink = (HtmlAnchor)table.getCellAt(1, 0).getFirstChild();
        HtmlPage editPage = firstCauseLink.click();

        DomElement modList = editPage.getElementById("modifications");
        int firstNbrOfModifications = modList.getChildNodes().size();

        editPage.getElementByName("_.comment").setTextContent("new comment");

        HtmlForm form = editPage.getFormByName("causeForm");
        submit(form);

        editPage = firstCauseLink.click();
        modList = editPage.getElementById("modifications");
        int secondNbrOfModifications = modList.getChildNodes().size();

        assertEquals(firstNbrOfModifications + 1, secondNbrOfModifications);
        assertStringContains("Latest modification date should be visible",
                modList.getFirstChild().asNormalizedText(), DateFormat.getDateTimeInstance(
                        DateFormat.SHORT, DateFormat.SHORT).format(cause.getLatestModification().getTime()));
    }

    //CS IGNORE MagicNumber FOR NEXT 100 LINES. REASON: TestData.
    /**
     * Tests that an error message is shown when there is no reachable Mongo database.
     * @throws Exception if so.
     */
    public void testNoMongoDB() throws Exception {
        KnowledgeBase kb = new MongoDBKnowledgeBase("someurl", 1234, "somedb", "user", Secret.fromString("pass"),
                false, false);
        Whitebox.setInternalState(PluginImpl.getInstance(), kb);
        WebClient web = createWebClient();
        HtmlPage page = web.goTo(CauseManagement.URL_NAME);
        DomElement element =  page.getElementById("errorMessage");
        assertNotNull(element);
    }

    /**
     * Verifies that the page is displaying the expected cause correctly.
     *
     * @param expectedCause the cause that is expected to be displayed.
     * @param editPage      the page to verify.
     * @see #testNewNavigation()
     * @see #testTableViewNavigation()
     */
    private void verifyCorrectCauseEditPage(FailureCause expectedCause, HtmlPage editPage) {
        HtmlForm form = editPage.getFormByName("causeForm");
        String actualId = form.getInputByName("_.id").getValueAttribute();
        if (Util.fixEmpty(expectedCause.getId()) == null) {
            assertNull(Util.fixEmpty(actualId));
        } else {
            assertEquals(expectedCause.getId(), actualId);
        }
        assertEquals(expectedCause.getName(), form.getInputByName("_.name").getValueAttribute());
        HtmlElement descrArea = form.getOneHtmlElementByAttribute("textarea", "name", "_.description");
        String description = descrArea.getTextContent();
        assertEquals(expectedCause.getDescription(), description);

        HtmlElement commentArea = form.getOneHtmlElementByAttribute("textarea", "name", "_.comment");
        String comment = commentArea.getTextContent();
        assertEquals(expectedCause.getComment(), comment);

        if (!expectedCause.getIndications().isEmpty()) {
            HtmlElement indicationsDiv = form.getOneHtmlElementByAttribute("div", "name", "indications");
            HtmlInput patternInput = indicationsDiv.getOneHtmlElementByAttribute("input", "name", "pattern");
            assertEquals(expectedCause.getIndications().get(0).getPattern().pattern(), patternInput.getValueAttribute());
        }
    }

    /**
     * Tests {@link CauseManagement#doRemoveConfirm(String, org.kohsuke.stapler.StaplerRequest,
     * org.kohsuke.stapler.StaplerResponse)}.
     * Assumes that the default {@link com.sonyericsson.jenkins.plugins.bfa.db.LocalFileKnowledgeBase} is used.
     *
     * @throws Exception if so.
     */
    public void testDoRemoveConfirm() throws Exception {
        FailureCause cause = new FailureCause("SomeName", "A Description");
        cause.addIndication(new BuildLogIndication("."));
        FailureCause cause1 = PluginImpl.getInstance().getKnowledgeBase().addCause(cause);
        cause = new FailureCause("SomeOtherName", "A Description");
        cause.addIndication(new BuildLogIndication("."));
        FailureCause cause2 = PluginImpl.getInstance().getKnowledgeBase().addCause(cause);

        KnowledgeBase kb = spy(PluginImpl.getInstance().getKnowledgeBase());
        Whitebox.setInternalState(PluginImpl.getInstance(), KnowledgeBase.class, kb);

        StaplerRequest request = mock(StaplerRequest.class);
        HttpSession session = mock(HttpSession.class);
        when(request.getSession(anyBoolean())).thenReturn(session);
        StaplerResponse response = mock(StaplerResponse.class);

        CauseManagement.getInstance().doRemoveConfirm(cause1.getId(), request, response);

        verify(kb).removeCause(eq(cause1.getId()));
        verify(session).setAttribute(eq(CauseManagement.SESSION_REMOVED_FAILURE_CAUSE), same(cause1));

        //Check that it is gone.
        assertNull(kb.getCause(cause1.getId()));

        //Check that the other one is still there
        assertSame(cause2, kb.getCause(cause2.getId()));
    }

    /**
     * Test Cause Management project action hiding.
     * @throws Exception if so.
     */
    public void testProjectCauseManagementActionIsHiddenWhenScanningDisabled() throws Exception {
        FreeStyleProject project = createFreeStyleProject();

        PluginImpl.getInstance().setGlobalEnabled(true);
        doScan(project, true);
        assertNotNull(project.getAction(TransientCauseManagement.class));

        doScan(project, false);
        assertNull(project.getAction(TransientCauseManagement.class));

        PluginImpl.getInstance().setGlobalEnabled(false);
        doScan(project, true);
        assertNull(project.getAction(TransientCauseManagement.class));

        doScan(project, false);
        assertNull(project.getAction(TransientCauseManagement.class));
    }

    /**
     * Turn project scanning on and off.
     *
     * @param project A project.
     * @param scan Scan or not.
     * @throws Exception if so.
     */
    private void doScan(FreeStyleProject project, boolean scan) throws Exception {
        project.removeProperty(ScannerJobProperty.class);
        project.addProperty(new ScannerJobProperty(!scan));
    }

}
