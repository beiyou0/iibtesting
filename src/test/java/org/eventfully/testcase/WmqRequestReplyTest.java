package org.eventfully.testcase;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.custommonkey.xmlunit.Diff;
import org.custommonkey.xmlunit.DifferenceListener;
import org.custommonkey.xmlunit.XMLUnit;
import org.custommonkey.xmlunit.exceptions.XpathException;
import org.eventfully.wmbtesting.EmptyRouteCamelConfiguration;
import org.eventfully.wmbtesting.IgnoreNamedElementsDifferenceListener;
import org.junit.*;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.xml.sax.SAXException;

import java.io.*;
import java.util.Properties;

import static junit.framework.Assert.assertNotNull;
import static org.custommonkey.xmlunit.XMLAssert.assertXMLEqual;
import static org.custommonkey.xmlunit.XMLAssert.assertXpathEvaluatesTo;

/**
 * Created by qianqian on 26/05/2017.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = EmptyRouteCamelConfiguration.class)
public class WmqRequestReplyTest {
    @Autowired
    public CamelContext camelContext;

    public ProducerTemplate producer;

    private static String resourcePath;

    @BeforeClass
    public static void setUpBeforeClass() throws IOException {
        resourcePath = WmqRequestReplyTest.class.getClass().getResource("/RequestReplyMQ").getPath();
    }


    @Before
    public void setUp() {
        XMLUnit.setIgnoreWhitespace(true);
        producer = camelContext.createProducerTemplate();
    }

    @After
    public void tearDown(){
        producer = null;
    }

    @Test
    public void requestReply01ByXpath() throws SAXException, IOException, XpathException {
        File testPayload = new File(resourcePath + "/request1.xml");
        String requestQ = "GET_REQREP_IN";
        String replyQ = "GET_REQREP_OUT";

        String reply = producer.requestBody("wmq:" + requestQ+ "?replyTo=" + replyQ +"&replyToType=Shared&useMessageIDAsCorrelationID=true",
                testPayload, String.class);

        assertNotNull(reply);
        assertXpathEvaluatesTo("Braithwaite", "/SaleEnvelope/SaleList/Invoice/Surname", reply);
    }

    @Test
    public void requestReply02ByXpath() throws SAXException, IOException, XpathException {
        File testPayload = new File(resourcePath + "/request2.xml");
        String requestQ = "GET_REQREP_IN";
        String replyQ = "GET_REQREP_OUT";

        String reply = producer.requestBody("wmq:" + requestQ+ "?replyTo=" + replyQ +"&replyToType=Shared&useMessageIDAsCorrelationID=true",
                testPayload, String.class);

        assertNotNull(reply);
        assertXpathEvaluatesTo("Palmer", "/SaleEnvelope/SaleList/Invoice/Surname", reply);
    }

    @Test
    public void requestReply03ByXML() throws IOException, SAXException {
        File testPayload = new File(resourcePath + "/request1.xml");
        File expectedPayload = new File(resourcePath + "/reply1.xml");
        String requestQ = "GET_REQREP_IN";
        String replyQ = "GET_REQREP_OUT";
        String[] ignoreNamedElementsNames = {"CompletionTime", "SomeOtherElement"};

        String reply = producer.requestBody("wmq:" + requestQ + "?replyTo=" + replyQ + "&replyToType=Shared&useMessageIDAsCorrelationID=true&jmsMessageType=Text",
                testPayload, String.class);
        assertNotNull(reply);

        Diff myDiff = new Diff(new FileReader(expectedPayload), new StringReader(reply));
        DifferenceListener diffListener = new IgnoreNamedElementsDifferenceListener(ignoreNamedElementsNames);
        myDiff.overrideDifferenceListener(diffListener);
        assertXMLEqual("Same elements/attributes sequences, but some values may be ignored, normally time and dates.", myDiff, true);
    }

    @Test
    public void requestReply04ByXML() throws IOException, SAXException {
        File testPayload = new File(resourcePath + "/request2.xml");
        File expectedPayload = new File(resourcePath + "/reply2.xml");
        String requestQ = "GET_REQREP_IN";
        String replyQ = "GET_REQREP_OUT";
        String[] ignoreNamedElementsNames = {"CompletionTime"};

        String reply = producer.requestBody("wmq:" + requestQ + "?replyTo=" + replyQ + "&replyToType=Shared&useMessageIDAsCorrelationID=true&jmsMessageType=Text",
                testPayload, String.class);
        assertNotNull(reply);

        Diff myDiff = new Diff(new FileReader(expectedPayload), new StringReader(reply));
        DifferenceListener diffListener = new IgnoreNamedElementsDifferenceListener(ignoreNamedElementsNames);
        myDiff.overrideDifferenceListener(diffListener);
        assertXMLEqual("Same elements/attributes sequences, but some values may be ignored, normally time and dates.", myDiff, true);
    }
}
